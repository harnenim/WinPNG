package moe.ohli.pngb;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.filechooser.FileNameExtensionFilter;

import moe.ohli.pngb.Logger.L;
import sun.awt.image.AbstractMultiResolutionImage;

@SuppressWarnings("serial")
public class GUI extends JFrame implements ActionListener, KeyListener {
	
	private static final String TMP_DIR = System.getProperty("java.io.tmpdir").replace('\\', '/') + "WinPNG/";
	private static final String CONFIG_FILE_PATH = TMP_DIR + "config.properties";
	private static final BufferedImage JUNK_IMAGE = new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR);
	static {
		JUNK_IMAGE.setRGB(0, 0, 0xFFFFFF);
	}
	
	private static Logger logger = new Logger(L.DEBUG);
	
	private File pngFile = null;
	private BufferedImage targetImage = null;
	private BufferedImage outputImage = null;
	
	private Properties props = new Properties();
	
	/**
	 * 컨테이너의 원본 파일명을 갖는 객체
	 * 
	 * @author harne_
	 *
	 */
	private static class ModelItem {
		public Container container;
		public String originalPath;
		public ModelItem(Container container, String originalPath) {
			this.container = container;
			this.originalPath = originalPath;
		}
		/**
		 * 리스트뷰에 출력할 텍스트
		 */
		@Override
		public String toString() {
			return container.path + " (" + comma(container.binary.length) + ")" + (originalPath == null ? "" : ": " + originalPath);
		}
		private String comma(int value) {
			String result = "" + (value % 1000);
			value /= 1000;
			while (value > 0) {
				result = (value % 1000) + "," + result;
				value /= 1000;
			}
			return result;
		}
	}
	
	/**
	 * 필요한 기능을 내포한 리스트뷰 객체
	 * 
	 * @author harne_
	 *
	 */
	private static class MyListView extends JList<ModelItem> {
		private DefaultListModel<ModelItem> model;
		
		public MyListView() {
			super(new DefaultListModel<>());
			this.model = (DefaultListModel<ModelItem>) getModel();
		}
		public int     count  ()              { return model.size   (); }
		public boolean isEmpty()              { return model.isEmpty(); }
		public void    clear  ()              {        model.clear  (); }
		public void    add   (ModelItem item) {        model.addElement   (item); }
		public boolean remove(ModelItem item) { return model.removeElement(item); }
		
		private void removeSelected() {
			for (ModelItem item : getSelectedValuesList()) {
				remove(item);
			}
		}
		public List<ModelItem> getAllItems() {
			List<ModelItem> items = new ArrayList<>();
			Enumeration<ModelItem> elements = model.elements();
			while (elements.hasMoreElements()) {
				items.add(elements.nextElement());
			}
			return items;
		}
		public List<Container> getAllContainers() {
			List<Container> containers = new ArrayList<>();
			Enumeration<ModelItem> elements = model.elements();
			while (elements.hasMoreElements()) {
				containers.add(elements.nextElement().container);
			}
			return containers;
		}
		public List<Container> getSelectedContainers() {
			List<Container> containers = new ArrayList<>();
			for (ModelItem item : getSelectedValuesList()) {
				containers.add(item.container);
			}
			return containers;
		}
		public void addContainers(List<Container> containers) {
			for (Container cont : containers) {
				add(new ModelItem(cont, null));
			}
		}
		private void sort() {
			List<ModelItem> items = getAllItems();
			Collections.sort(items, new Comparator<ModelItem>() {
				@Override
				public int compare(ModelItem o1, ModelItem o2) {
					return o1.container.path.compareTo(o2.container.path);
				}
			});
			clear();
			for (ModelItem item : items) {
				add(item);
			}
		}
	}
    
    /**
     * 기본 스타일 적용된 버튼
     * 
     * @author harne_
     *
     */
    private static class MyButton extends JButton {
    	private static final Color COLOR_EEEEEE = new Color(0xEEEEEE);
    	public MyButton(GUI gui) {
    		super();
    		setBackground(COLOR_EEEEEE);
    		addActionListener(gui);
    		addKeyListener(gui);
    	}
    }

	// 윗줄 PNG 파일 읽기
	private JPanel panelPngFile = new JPanel(new BorderLayout());
	private JTextField tfPngFile = new JTextField();
	private JButton btnOpen = new MyButton(this), btnClose = new MyButton(this);
	
	// 좌측 내용물
	private JPanel panelFilesEdit = new JPanel(new BorderLayout());
	private MyListView lvFiles = new MyListView();
	private JButton btnAddFile = new MyButton(this), btnDelete = new MyButton(this);
	private JPanel panelExport = new JPanel(new BorderLayout());
	private JTextField tfExportDir = new JTextField();
	private JButton btnExport = new MyButton(this);
	
	// 우측 이미지
	private JPanel panelPreview = new JPanel();
	private JLabel ivTarget = new JLabel(), jlTarget = new JLabel(), jlPw    = new JLabel()
	             , ivOutput = new JLabel(), jlOutput = new JLabel(), jlWidth = new JLabel();
	private JRadioButton rbTarget011 = new JRadioButton();
	private JRadioButton rbTarget114 = new JRadioButton();
	private JRadioButton rbTarget149 = new JRadioButton();
	private ButtonGroup rbGroupTarget = new ButtonGroup();
	private JTextField tfPw = new JTextField(""), tfWidth = new JTextField("0");
	private JButton btnSave = new MyButton(this), btnCopy = new MyButton(this);
	
	private static boolean USE_JFC = false;
	
	private JFileChooser fcPng = new JFileChooser();
	
	public void init() {
		setTitle("WinPNG");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		boolean isAndroid = false;
		String exportDir = null;
		
		{	// 설정 가져오기
			FileInputStream fis = null;
			try {
				System.out.println("설정 파일 경로: " + CONFIG_FILE_PATH);
				props.load(fis = new FileInputStream(new File(CONFIG_FILE_PATH)));
			} catch (Exception e) {
				System.out.println("설정 가져오기 실패");
			} finally {
				if (fis != null) try { fis.close(); } catch (Exception e) { }
			}
			
			// 로그 설정
			if (!"N".equals(props.getProperty("LogToFile"))) {
				props.setProperty("LogToFile", "Y");
				File logDir = new File(TMP_DIR + "log");
				logDir.mkdirs();
				
				String logPath = TMP_DIR + "log/" + Calendar.getInstance().getTimeInMillis() + ".log";
				System.out.println("로그 파일: " + logPath);
				File logFile = new File(logPath);
				try {
					logger.add(new PrintStream(logFile));
				} catch (FileNotFoundException e) {
					System.out.println("로그 파일 설정 실패");
					e.printStackTrace();
				}
			}
			try {
				String logLevel = props.getProperty("LogLevel");
				logger.setDefaultLevel(L.valueOf(logLevel));
				logger.log(L.INFO, "로그 레벨: " + logLevel);
			} catch (Exception e) {
				logger.log(L.ERROR, "로그 레벨 설정 가져오기 실패");
				logger.log(L.DEBUG, e);
			}
			logger.setDefaultLevel(L.INFO);
			
			boolean isLinux = "Linux".equals(System.getProperty("os.name"));
			
			// 창 위치
			try {
				// 설정에서 위치 가져오기
				String[] bounds = props.getProperty("bounds").split(",");
				if (bounds.length < 4) throw new Exception("bounds setting error");
				
				setBounds(Integer.parseInt(bounds[0]), Integer.parseInt(bounds[1]), Integer.parseInt(bounds[2]), Integer.parseInt(bounds[3]));
				
			} catch (Exception e) {
				logger.warn("창 위치 설정 가져오기 실패");
				logger.debug(e);
				
				// 실패 시 기본값
				Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
				int width  = Math.min(800, screenSize.width );
				int height = Math.min(600, screenSize.height);
				setBounds((screenSize.width - width) / 2, (screenSize.height - height) / 2, width, height);
			}
			setMinimumSize(new Dimension(600, 520));
			
			// 기타 설정
			try {
				String useTargetImage = props.getProperty("useTargetImage");
				if ("114".equals(useTargetImage)) {
					rbTarget114.setSelected(true);
				} else if ("149".equals(useTargetImage)) {
					rbTarget149.setSelected(true);
				} else {
					rbTarget011.setSelected(true);
				}
			} catch (Exception e) {
				logger.warn("이미지 입력 설정 가져오기 실패");
				logger.debug(e);
			}
			try {
				tfPw.setText(props.getProperty("password"));
			} catch (Exception e) {
				logger.warn("비밀번호 가져오기 실패");
				logger.debug(e);
			}
			try {
				int minWidth = Integer.parseInt(props.getProperty("minWidth"));
				tfWidth.setText("" + minWidth);
			} catch (Exception e) {
				logger.warn("최소 크기 설정 가져오기 실패");
				logger.debug(e);
				tfWidth.setText("0");
			}
			try {
				exportDir = props.getProperty("exportDir");
			} catch (Exception e) {
				logger.warn("추출 경로 설정 가져오기 실패");
				logger.debug(e);
			}
			
			if (isLinux) {
				if (isAndroid = confirm("Is this Android?", "OS Check")) {
					// Android JRE 실행을 가정
					Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
					int width  = Math.min(800, screenSize.width);
					int height = 520;
					setBounds((screenSize.width - width) / 2, (screenSize.height - height) / 2, width, height);
					
					// 다운로드 폴더 내 WinPNG를 기본 추출 폴더로 설정
					exportDir = "/storage/emulated/0/Download/WinPNG";
					
					// OS 자체 파일 선택기 사용 불가
					USE_JFC = true;
				}
			} else {
				// 시스템 언어 가져오기
				String language = Locale.getDefault().getLanguage();
				if ("ko".equals(language)) {
					Strings.setLanguage(Strings.Language.KR);
				}
			}
		}
		
		// 2023년 사용 제한
		if (Calendar.getInstance().get(Calendar.YEAR) != 2023) {
			setVisible(true);
			setEnabled(true);
			alert("ㅋㅋ?ㅎㅎ!");
			dispose();
			return;
		}
		
		{	// 문자열 설정
			if (!isAndroid) {
				tfPngFile.setText(Strings.get("open placeholder"));
			}
			btnOpen .setText(Strings.get("open"));
			btnClose.setText(Strings.get("close"));
			
			btnAddFile.setText(Strings.get("add"));
			btnDelete .setText(Strings.get("delete"));
			if (exportDir.length() > 0) {
				tfExportDir.setText(exportDir);
			} else {
				tfExportDir.setText(Strings.get("export placeholder"));
			}
			btnExport.setText(Strings.get("export"));
			
			ivTarget.setToolTipText(Strings.get("target tooltip"));
			ivOutput.setToolTipText(Strings.get("output tooltip"));
			jlTarget.setText(Strings.get("input image"));
			rbTarget011.setText(Strings.get("dont use"));
			rbTarget114.setText("1:1:4");
			rbTarget149.setText("1:4:9");
			jlOutput.setText(Strings.get("output image"));
			jlPw.setText("   " + Strings.get("password") + " ");
			jlWidth.setText("      " + Strings.get("min width") + " ");
			btnSave.setText(Strings.get("save"));
			btnCopy.setText(Strings.get("copy"));
		}
		
		setLayout(new BorderLayout());
		
		{	// 상단 PNG 파일 읽기
			tfPngFile.setBackground(Color.WHITE);
			panelPngFile.add(tfPngFile, BorderLayout.CENTER);
			
			JPanel panelPngBtn = new JPanel(new GridLayout(1, 2));
			panelPngBtn.add(btnOpen);
			panelPngBtn.add(btnClose);
			panelPngFile.add(panelPngBtn, BorderLayout.EAST);
			
			add(panelPngFile, BorderLayout.NORTH);
		}
		
		{	// 중앙 내용물
			JPanel panelFiles = new JPanel(new BorderLayout());
			{	// 파일 리스트 영역
				panelFilesEdit.add(new JScrollPane(lvFiles), BorderLayout.CENTER);
				JPanel panelFilesBtn = new JPanel(new FlowLayout(FlowLayout.LEFT));
				panelFilesBtn.add(btnAddFile);
				panelFilesBtn.add(btnDelete);
				panelFilesEdit.add(panelFilesBtn , BorderLayout.SOUTH);
				panelFiles.add(panelFilesEdit, BorderLayout.CENTER);
			}
			{	// 버튼 영역
				panelExport.add(tfExportDir, BorderLayout.CENTER);
				panelExport.add(btnExport  , BorderLayout.EAST);
				panelFiles.add(panelExport, BorderLayout.SOUTH);
			}
			add(panelFiles, BorderLayout.CENTER);
		}
		{	// 우측 이미지
			JPanel panelRight  = new JPanel(new BorderLayout());
			JPanel panelTarget = new JPanel(new BorderLayout());
			JPanel panelOutput = new JPanel(new BorderLayout());
			{	// 이미지뷰 영역
				panelPreview.setLayout(new BoxLayout(panelPreview, BoxLayout.Y_AXIS));
				panelPreview.add(new JPanel());
				{	// 입력 이미지
					JPanel panelRadio = new JPanel();
					panelTarget.add(jlTarget, BorderLayout.NORTH);
					panelTarget.add(ivTarget, BorderLayout.CENTER);
					panelRadio.add(rbTarget011);
					panelRadio.add(rbTarget114);
					panelRadio.add(rbTarget149);
					rbGroupTarget.add(rbTarget011);
					rbGroupTarget.add(rbTarget114);
					rbGroupTarget.add(rbTarget149);
					panelTarget.add(panelRadio, BorderLayout.SOUTH);
					panelPreview.add(panelTarget);
				}
				panelPreview.add(new JPanel());
				{	// 출력 이미지
					JPanel panelPw = new JPanel(new BorderLayout());
					panelOutput.add(jlOutput, BorderLayout.NORTH);
					panelOutput.add(ivOutput, BorderLayout.CENTER);
					panelPw.add(jlPw, BorderLayout.WEST);
					panelPw.add(tfPw, BorderLayout.CENTER);
					panelOutput.add(panelPw, BorderLayout.SOUTH);
					panelPreview.add(panelOutput);
				}
				panelPreview.add(new JPanel());
				panelRight.add(panelPreview, BorderLayout.CENTER);
			}
			{	// 버튼 영역
				JPanel panelSave = new JPanel(new BorderLayout());
				panelSave.add(jlWidth, BorderLayout.WEST);
				panelSave.add(tfWidth, BorderLayout.CENTER);
				
				JPanel panelSaveBtn = new JPanel(new GridLayout(1, 2));
				panelSaveBtn.add(btnCopy);
				panelSaveBtn.add(btnSave);
				panelSave.add(panelSaveBtn, BorderLayout.EAST);
				
				panelRight.add(panelSave, BorderLayout.SOUTH);
			}
			
			panelTarget.setMaximumSize(new Dimension(280, 200));
			panelOutput.setMaximumSize(new Dimension(280, 180));
			
			updateTarget(JUNK_IMAGE);
			updateOutput();
			
			add(panelRight, BorderLayout.EAST);
		}
		
		{	// 이벤트 설정
			lvFiles.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
			lvFiles.setDragEnabled(true);
			
			for (Component c : new Component[] { this, lvFiles, tfPw, tfWidth, tfPngFile, rbTarget011, rbTarget114, rbTarget149 }) {
				c.addKeyListener(this);
			}
			for (AbstractButton c : new AbstractButton[] { rbTarget011, rbTarget114, rbTarget149 }) {
				c.addActionListener(this);
			}
			
			FileTransferHandler fth = new FileTransferHandler();
			lvFiles .setTransferHandler(fth);
			ivTarget.setTransferHandler(fth);
			ivOutput.setTransferHandler(fth);
			ivTarget.addMouseListener(new ImageDragAdaptor(ivTarget, fth));
			ivOutput.addMouseListener(new ImageDragAdaptor(ivOutput, fth));
			
			panelPngFile  .setDropTarget(new FileDropTarget(this, panelPngFile  ));
			tfPngFile     .setDropTarget(new FileDropTarget(this, panelPngFile  ));
			lvFiles       .setDropTarget(new FileDropTarget(this, panelFilesEdit));
			panelFilesEdit.setDropTarget(new FileDropTarget(this, panelFilesEdit));
			panelExport   .setDropTarget(new FileDropTarget(this, panelExport   ));
			tfExportDir   .setDropTarget(new FileDropTarget(this, panelExport   ));
			panelPreview  .setDropTarget(new FileDropTarget(this, panelPreview  ));
			ivTarget      .setDropTarget(new FileDropTarget(this, panelPreview  ));
			ivOutput      .setDropTarget(new FileDropTarget(this, panelPreview  ));
			
			// 종료 이벤트 시 설정 저장
			addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent evt) {
					super.windowClosing(evt);

					Rectangle bounds = getBounds();
					props.setProperty("LogLevel", logger.getDefaultLevel().name());
					props.setProperty("bounds", bounds.x + "," + bounds.y + "," + bounds.width + "," + bounds.height);
					props.setProperty("password", tfPw.getText());
					props.setProperty("minWidth", tfWidth.getText());
					props.setProperty("exportDir", tfExportDir.getText());
					if (rbTarget011.isSelected()) {
						props.setProperty("useTargetImage", "011");
					} else if (rbTarget114.isSelected()) {
						props.setProperty("useTargetImage", "114");
					} else if (rbTarget149.isSelected()) {
						props.setProperty("useTargetImage", "149");
					}
					
					FileOutputStream fos = null;
					try {
						props.store(fos = new FileOutputStream(new File(CONFIG_FILE_PATH)), "WinPNG config");
					} catch (Exception e) {
						logger.error("설정 저장 실패");
						logger.debug(e);
					} finally {
						if (fos != null) try { fos.close(); } catch (Exception e) { }
					}
					
					// 임시 디렉토리는 deleteOnExit 동작이 잘 안 돼서
					// 아예 빼버리고 이쪽에서 삭제함
					try {
						File[] files = new File(TMP_DIR).listFiles();
						for (File file : files) {
							if (file.isDirectory()) {
								try {
									// 디렉토리명이 숫자면 임시 폴더로 간주
									Long.parseLong(file.getName());
									deleteDirectory(file);
								} catch (Exception e) {}
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
		}

		if (USE_JFC) {
			fcPng.setFileFilter(new FileNameExtensionFilter(Strings.get("FileFilter-PNG"), "png"));
			fcPng.setFileSelectionMode(JFileChooser.FILES_ONLY);
			fcPng.setMultiSelectionEnabled(false);
		}

		setVisible(true);
		setEnabled(true);
	}
	/**
	 * 디렉토리 삭제
	 * @param file
	 * @return
	 */
	private int deleteDirectory(File file) {
		logger.info("deleteDirectory: " + file.getAbsolutePath());
		int count = 0;
		if (file.isDirectory()) {
			// 디렉토리일 경우 하위 디렉토리/파일 먼저 삭제
			for (File subFile : file.listFiles()) {
				count += deleteDirectory(subFile);
			}
		}
		file.delete();
		count++;
		return count;
	}
	
	/**
	 * 출력물 재생성
	 * @return 성공여부
	 */
	private void updateOutput() {
		logger.info("updateOutput");
		if (updateStatus > 0) {
			logger.info("이전 이미지 생성 중... 대기");
			updateStatus = 2;
			return;
		}
		do {
			if (lvFiles.isEmpty()) {
				outputImage = null;
				ivOutput.setIcon(new ImageIcon(JUNK_IMAGE.getScaledInstance(280, 158, Image.SCALE_SMOOTH)));
				jlOutput.setText(Strings.get("output image"));
				break;
			}
			
			int inputMinWidth = 0;
			try {
				inputMinWidth = Integer.parseInt(tfWidth.getText().trim());
			} catch (Exception e) {
				logger.warn("올바른 크기를 입력하세요.");
				logger.debug(e);
				alert(Strings.get("warn-width-input"));
				break;
			}
			
			final int minWidth = inputMinWidth;
			if (minWidth > 1920) {
				logger.warn("최소 크기가 너무 큽니다.");
				alert(Strings.get("warn-width-large"));
				break;
			}
			
			// 별도 스레드에서 진행
			new Thread(new Runnable() {
				@Override
				public void run() {
					List<Container> containers = lvFiles.getAllContainers();
					Collections.shuffle(containers); // 섞어서 난수화, 불러올 때 정렬
					try {
						String password = tfPw.getText();
						if (rbTarget114.isSelected()) {
							outputImage = new Container.WithTarget(targetImage, containers).toBitmap114(minWidth, password);
						} else if (rbTarget149.isSelected()) {
							outputImage = new Container.WithTarget(targetImage, containers).toBitmap149(minWidth, password);
						} else {
							outputImage = Container.toBitmapTwice(containers, password, minWidth);
						}
						ivOutput.setIcon(new ImageIcon(outputImage.getScaledInstance(280, 158, Image.SCALE_SMOOTH)));
						jlOutput.setText(Strings.get("output image") + "(" + outputImage.getWidth() + "×" + outputImage.getHeight() + ")");
						
					} catch (Exception e) {
						logger.error("이미지 생성 실패");
						logger.debug(e);
					}
					
					if (--updateStatus > 0) {
						logger.info("이미지 생성 대기열 실행");
						updateOutput();
					}
				}
			}).start();
			return;
		} while (false);
		
		// 이미지 생성 로직 안 돌면 바로 이쪽으로 옴
		if (--updateStatus > 0) {
			logger.info("이미지 생성 대기열 실행");
			updateOutput();
		}
	}
	private int updateStatus = 0;
	
	/**
	 * 출력물에 활용할 이미지 설정
	 * @return
	 */
	private void updateTarget(BufferedImage image) {
		logger.info("updateTarget");
		ivTarget.setIcon(new ImageIcon((targetImage = image).getScaledInstance(280, 158, Image.SCALE_SMOOTH)));
		if (image != null) {
			jlTarget.setText(Strings.get("input image") + "(" + image.getWidth() + "×" + image.getHeight() + ")");
		}
	}
	
	/**
	 * PNG 파일 열기 대화상자
	 */
	private void openPng() {
		if (USE_JFC) {
			if (pngFile != null) {
				// 현재 열려있는 파일이 있을 경우 해당 경로에서 열기
				fcPng.setSelectedFile(pngFile);
			}
			int result = fcPng.showOpenDialog(this);
			if (result == JFileChooser.APPROVE_OPTION) {
				if (!openPng(fcPng.getSelectedFile().getAbsolutePath())) {
					alert(Strings.get("cant parse"));
				}
			}
		} else {
			if (fdOpen == null) {
				fdOpen = new FileDialog(this, Strings.get("open png"), FileDialog.LOAD);
				fdOpen.setFile("*.png");
				fdOpen.setFilenameFilter(new FilenameFilter() {
					@Override
					public boolean accept(File dir, String name) {
						return name.toLowerCase().endsWith(".png");
					}
				});
			}
			if (pngFile != null) {
				// 현재 열려있는 파일이 있을 경우 해당 경로에서 열기
				String path = pngFile.getAbsolutePath().replace("\\", "/");
				fdOpen.setDirectory(path.substring(0, path.lastIndexOf("/")));
			}
			fdOpen.setVisible(true);
			String filename = fdOpen.getFile();
			if (filename != null) {
				if (!openPng(fdOpen.getDirectory() + filename)) {
					alert(Strings.get("cant parse"));
				}
			}
		}
	}
	private FileDialog fdOpen = null;
	/**
	 * PNG 파일 열기
	 * @param path
	 * @return 성공여부
	 */
	private boolean openPng(String path) {
		logger.info("openPng: " + path);
		try {
			BufferedImage bmp = null;
			
			File file = null;
			
			if (path.indexOf("http:") >= 0) {
				path = path.substring(path.indexOf("http:")).replace('\\', '/').replace(":/", "://").replace("///", "//");
				bmp = ImageIO.read(fileFromUrl(path));
				
			} else if (path.indexOf("https:") >= 0) {
				path = path.substring(path.indexOf("https:")).replace('\\', '/').replace(":/", "://").replace("///", "//");
				try {
					bmp = ImageIO.read(fileFromUrl(path));
				} catch (Exception e) {
					logger.debug(e);
					bmp = ImageIO.read(fileFromUrl("http://" + path.substring(8)));
				}
				
			} else {
				file = new File(path);
				bmp = ImageIO.read(file);
			}

			if (openBitmap(bmp, file)) {
				return true;
			}
			
		} catch (Exception e) {
			logger.warn("PNG 파일 열기 실패");
			logger.debug(e);
		}
		return false;
	}
	private boolean openBitmap(BufferedImage bmp) {
		return openBitmap(bmp, null);
	}
	/**
	 * 비트맵 이미지 해석
	 * @param bmp
	 * @param file
	 * @return 성공여부
	 */
	private boolean openBitmap(BufferedImage bmp, File file) {
		try {
			Container.WithTarget parsed = Container.WithTarget.fromBitmap(bmp);
			if (parsed == null) {
				// 키값 가지고 재시도
				String key = JOptionPane.showInputDialog(this, "비밀번호가 있다면 키를 입력하세요.");
				parsed = Container.WithTarget.fromBitmap(bmp, key);
			}
			if (parsed == null) {
				return false;
			}
			
			// 파일에서 열었을 경우 열려있는 파일 설정
			if (file != null) {
				pngFile = file;
				tfPngFile.setText(pngFile.getAbsolutePath());
			}
			
			lvFiles.clear();
			lvFiles.addContainers(parsed.containers);
			lvFiles.sort();
			updateTarget(parsed.targetImage == null ? JUNK_IMAGE : parsed.targetImage);
			updateOutput();
			
			return true;
			
		} catch (Exception e) {
			logger.warn("이미지 해석 실패");
			logger.debug(e);
		}
		return false;
	}
	/**
	 * 출력물에 활용할 이미지 열기
	 * @param path
	 * @return 성공여부
	 */
	private boolean setTargetImage(String path) {
		logger.info("setTargetImage: " + path);
		try {
			if (path.indexOf("http:") >= 0) {
				path = path.substring(path.indexOf("http:")).replace('\\', '/').replace(":/", "://").replace("///", "//");
				updateTarget(ImageIO.read(fileFromUrl(path)));
				
			} else if (path.indexOf("https:") >= 0) {
				path = path.substring(path.indexOf("https:")).replace('\\', '/').replace(":/", "://").replace("///", "//");
				updateTarget(ImageIO.read(fileFromUrl(path)));
				
			} else {
				File newPng = new File(path);
				updateTarget(ImageIO.read(newPng));
			}
			if (rbTarget114.isSelected() || rbTarget149.isSelected()) {
				updateOutput();
			}
			return true;
			
		} catch (Exception e) {
			logger.warn("이미지 가져오기 실패");
			logger.debug(e);
		}
		return false;
	}
	
	/**
	 * 클립보드에서 붙여넣기
	 * @param tr
	 * @return 성공여부
	 */
	@SuppressWarnings("unchecked")
	private boolean pasteFromClipboard(Transferable tr, Component c) {
		logger.info("pasteTargetImage");
		if (tr.isDataFlavorSupported(DataFlavor.imageFlavor)) {
			logger.info("이미지일 경우 -> 출력물에 활용할 이미지로 붙여넣기");
			try {
				BufferedImage image = (BufferedImage) tr.getTransferData(DataFlavor.imageFlavor);
				if (c == tfPngFile) {
					openBitmap(image);
				} else {
					updateTarget(image);
					if (rbTarget114.isSelected() || rbTarget149.isSelected()) {
						updateOutput();
					}
				}
				return true;
				
			} catch (Exception e) {
				logger.warn("이미지 붙여넣기 실패");
				logger.debug(e);
			}
		} else if (tr.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
			logger.info("파일일 경우 -> 파일 목록 or 출력물 이미지로 붙여넣기");
			try {
				dropFiles((List<File>) tr.getTransferData(DataFlavor.javaFileListFlavor), ((c == lvFiles) ? lvFiles : panelPreview));
			} catch (Exception e) {
				logger.warn("파일 붙여넣기 실패");
				logger.debug(e);
			}
		} else if (tr.isDataFlavorSupported(DataFlavor.stringFlavor)) {
			if (c == tfPngFile || c == tfExportDir) {
				return false;
			}
			logger.info("텍스트일 경우: 출력물에 활용할 이미지 경로 붙여넣기");
			try {
				setTargetImage((String) tr.getTransferData(DataFlavor.stringFlavor));
			} catch (Exception e) {
				e.printStackTrace();
			}
			
		} else {
			logger.info("이미지가 아님");
			for (DataFlavor flavor : tr.getTransferDataFlavors()) {
				logger.debug(flavor.getMimeType());
			}
			logger.debug("allHtmlFlavor      : " + tr.isDataFlavorSupported(DataFlavor.allHtmlFlavor      ));
			logger.debug("fragmentHtmlFlavor : " + tr.isDataFlavorSupported(DataFlavor.fragmentHtmlFlavor ));
			logger.debug("selectionHtmlFlavor: " + tr.isDataFlavorSupported(DataFlavor.selectionHtmlFlavor));
			logger.debug("stringFlavor       : " + tr.isDataFlavorSupported(DataFlavor.stringFlavor       ));
		}
		return false;
	}
	
	/**
	 * 이미지 저장 가능한지 확인 후 저장
	 */
	private void checkAndSave() {
		if (outputImage == null) {
			alert(Strings.get("empty image"));
		} else if (pngFile == null) {
			savePng();
		} else {
			savePng(pngFile);
		}
	}
	
	/**
	 * PNG 파일 저장 대화상자 열기
	 */
	private void savePng() {
		if (USE_JFC) {
			if (pngFile != null) {
				// 현재 열려있는 파일이 있을 경우 해당 경로에서 열기
				fcPng.setSelectedFile(pngFile);
			}
			int result = fcPng.showSaveDialog(this);
			if (result == JFileChooser.APPROVE_OPTION) {
				if (!savePng(fcPng.getSelectedFile())) {
					alert(Strings.get("cant save"));
				}
			}
		} else {
			if (fdSave == null) {
				fdSave = new FileDialog(this, Strings.get("save png"), FileDialog.SAVE);
				fdSave.setFile("*.png");
				fdSave.setFilenameFilter(new FilenameFilter() {
					@Override
					public boolean accept(File dir, String name) {
						return name.toLowerCase().endsWith(".png");
					}
				});
			}
			if (pngFile != null) {
				// 현재 열려있는 파일이 있을 경우 해당 경로에서 열기
				String path = pngFile.getAbsolutePath().replace("\\", "/");
				fdSave.setDirectory(path.substring(0, path.lastIndexOf("/")));
				fdSave.setFile(pngFile.getName());
			}
			fdSave.setVisible(true);
			String filename = fdSave.getFile();
			if (filename != null) {
				if (!filename.toLowerCase().endsWith(".png")) {
					filename += ".png";
				}
				if (!savePng(new File(fdSave.getDirectory() + filename))) {
					alert(Strings.get("cant save"));
				}
			}
		}
	}
	private FileDialog fdSave = null;
	/**
	 * PNG 파일 저장
	 * @param file
	 * @return 성공여부
	 */
	private boolean savePng(File file) {
		logger.info("savePng");
		try {
			ImageIO.write(outputImage, "PNG", file);
			tfPngFile.setText((pngFile = file).getAbsolutePath());
			return true;
			
		} catch (Exception e) {
			logger.error("PNG 파일 저장 실패");
			logger.debug(e);
		}
		return false;
	}
	/**
	 * 출력 이미지 클립보드로 복사
	 */
	private void copyToClipboard() {
		logger.info("copyToClipboard");
		final Image image = outputImage;
		if (outputImage != null) {
			try {
				Transferable contents = new Transferable() {
					@Override
					public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
						logger.debug("getTransferData: " + Calendar.getInstance().getTimeInMillis());
						if (flavor.equals(DataFlavor.imageFlavor) && image != null) {
							return image;
						} else {
							throw new UnsupportedFlavorException(flavor);
						}
					}
					@Override
					public DataFlavor[] getTransferDataFlavors() {
						return new DataFlavor[] { DataFlavor.imageFlavor };
					}
					@Override
					public boolean isDataFlavorSupported(DataFlavor flavor) {
						for (DataFlavor f : getTransferDataFlavors()) {
							if (f.equals(flavor)) {
								return true;
							}
						}
						return false;
					}
				};
				
				logger.debug("copy start: " + Calendar.getInstance().getTimeInMillis());
				Toolkit.getDefaultToolkit().getSystemClipboard().setContents(contents, null);
				logger.debug("copy end: " + Calendar.getInstance().getTimeInMillis());
				
				alert(Strings.get("image copied"));
				
			} catch (Exception e) {
				logger.error("이미지 복사 실패");
				logger.debug(e);
				alert(Strings.get("clipboard fail"));
			}
		}
	}

	/**
	 * 파일 추가
	 * @param files
	 */
	private void addFiles(File[] files) {
		logger.info("addFiles");
		List<ModelItem> items = new ArrayList<>();
		List<String> paths = new ArrayList<>();
		for (ModelItem item : lvFiles.getAllItems()) {
			items.add(item);
			paths.add(item.container.path);
		}
		if (files.length > 0) {
			if (files[0].getAbsolutePath().replace('\\', '/').startsWith(TMP_DIR)) {
				// 임시 파일이면 잘못 드래그한 경우
				logger.info("임시 파일 무시");
				return;
			}
		}
		int count = 0;
		for (File file : files) {
			try {
				for (Container cont : Container.fileToContainers(file)) {
					if (paths.contains(cont.path)) {
						if (confirm(Strings.get("file duplicated message") + cont.path, Strings.get("file duplicated title"))) {
							// 기존 것 찾아서 삭제
							for (ModelItem item : items) {
								if (cont.path.equals(item.container.path)) {
									lvFiles.remove(item);
								}
							}
						} else {
							// 추가 안 하고 넘김
							continue;
						}
					}
					lvFiles.add(new ModelItem(cont, file.getParent() + "/" + cont.path));
					count++;
				}
			} catch (Exception e) {
				logger.error("파일 추가 실패");
				logger.debug(e);
			}
		}
		if (count > 0) {
			lvFiles.sort();
			updateOutput();
		}
	}
	
	/**
	 * 선택된 파일 삭제
	 */
	private void deleteSelected() {
		logger.info("deleteSelected");
		lvFiles.removeSelected();
		updateOutput();
	}
	
	/**
	 * 선택된 파일 추출
	 */
	private void exportSelected() {
		String exportPath = tfExportDir.getText();
		if (exportPath.replace('\\', '/').startsWith(System.getProperty("java.io.tmpdir").replace('\\', '/'))) {
			alert(Strings.get("cant export to temp dir"));
			return;
		}
		logger.info("export to: " + exportPath);

		exportSelected(exportPath, true);
	}
	/**
	 * 선택된 파일 추출 - main에서 호출
	 * @param exportPath: 추출할 경로
	 * @param useAlert: 추출 성공 시 메시지 표시 여부
	 */
	private void exportSelected(String exportPath, boolean useAlert) {
		List<Container> containers = lvFiles.getSelectedContainers();
		if (containers.size() == 0) {
			showMessage(Strings.get("file not selected"), useAlert);
			return;
		}
		
		List<File> files = Container.containersToFiles(containers, exportPath);
		if (files.size() > 0) {
			showMessage(Strings.get("file exported"), useAlert);
		} else {
			showMessage(Strings.get("cant export file"), useAlert);
		}
	}
	private void showMessage(String msg, boolean useAlert) {
		if (useAlert) {
			alert(msg);
		} else {
			logger.info(msg);
		}
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		Object target = e.getSource();
		
		if (target == btnOpen) {
			// PNG 파일 열기
			openPng();
			
		} else if (target == btnClose) {
			// PNG 파일 닫기
			lvFiles.clear();
			pngFile = null;
			tfPngFile.setText("");
			updateTarget(JUNK_IMAGE);
			updateOutput();
			
		} else if (target == btnAddFile) {
			// 파일 추가
			if (pngFile != null) {
				// 현재 열려있는 파일이 있을 경우 해당 경로에서 열기
				String path = pngFile.getAbsolutePath().replace("\\", "/");
				path = path.substring(0, path.lastIndexOf("/"));
				fcFile.setCurrentDirectory(new File(path));
			}
			fcFile.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
			fcFile.setMultiSelectionEnabled(true);
			int result = fcFile.showOpenDialog(this);
			if (result == JFileChooser.APPROVE_OPTION) {
				addFiles(fcFile.getSelectedFiles());
			}
			
		} else if (target == btnDelete) {
			// 선택된 파일 삭제
			deleteSelected();
			
		} else if (target == btnExport) {
			exportSelected();
			
		} else if (target == btnSave) {
			// PNG 파일 저장
			checkAndSave();
			
		} else if (target == btnCopy) {
			// 이미지 클립보드 복사
			copyToClipboard();
			
		} else if (target == rbTarget011
				|| target == rbTarget114
				|| target == rbTarget149
				) {
			// 이미지 사용 여부 선택하면 재생성
			updateOutput();
		}
	}
	private JFileChooser fcFile = new JFileChooser();

	@Override
	public void keyTyped(KeyEvent e) {
		logger.all("keyTyped: " + e.getKeyCode());
	}

	@Override
	public void keyPressed(KeyEvent e) {
		switch (e.getKeyCode()) {
			case 67: { // C
				if (e.isControlDown()) {
					// Ctrl+C: 이미지 클립보드 복사
					copyToClipboard();
				}
				break;
			}
			case 68: { // C
				if (e.isAltDown()) {
					// Alt+D: 주소 줄로 이동
					tfPngFile.requestFocus();
				}
				break;
			}
			case 79: { // O
				if (e.isControlDown()) {
					// Ctrl+O: PNG 이미지 열기
					openPng();
				}
				break;
			}
			case 83: { // S
				if (e.isControlDown()) {
					// Ctrl+S: PNG 이미지 저장
					checkAndSave();
				}
				break;
			}
			case 86: { // V
				if (e.isControlDown()) {
					Component c = e.getComponent();
					if (c == tfExportDir) {
						// 기본 텍스트 붙여넣기
						break;
					}
					if (c == tfWidth) {
						// 최소 크기 입력엔 붙여넣기 금지
						e.consume();
					}
					// Ctrl+V: 이미지 붙여넣기
					pasteFromClipboard(Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null), e.getComponent());
				}
				break;
			}
			case 10: { // Enter
				Component c = e.getComponent();
				if (c == tfPngFile) {
					// 이미지 경로로 파일 열기..에 엔터 칠 사람이 있나?
					if (!openPng(tfPngFile.getText())) {
						alert(Strings.get("cant parse path"));
					}
				} else if (c == tfWidth) {
					try {
						// 최소 크기 변경 없어도 엔터면 이미지 재생성
						Integer.parseInt(tfWidth.getText().trim());
						updateOutput();
					} catch (Exception ex) {
						logger.warn("올바른 크기를 입력하세요.");
						logger.debug(ex);
						alert(Strings.get("warn-width-input"));
					}
				}
				break;
			}
			default: {
				logger.all("keyPressed: " + e.getKeyCode());
			}
		}
	}

	@Override
	public void keyReleased(KeyEvent e) {
		Component component = e.getComponent();
		if (component == tfWidth) {
			if (e.getKeyCode() == 10) {
				// 엔터일 경우 KeyPressed에서 이미 동작함
				return;
			}
			String strWidth = tfWidth.getText().trim();
			try {
				// 최소 크기 변경 이미지 재생성
				Integer.parseInt(strWidth);
				updateOutput();
			} catch (Exception ex) {
				logger.warn("올바른 크기를 입력하세요.");
				logger.debug(ex);
				alert(Strings.get("warn-width-input"));
				
				char[] cs = strWidth.toCharArray();
				strWidth = "";
				for (char c : cs) {
					if (c >= '0' && c <= '9') {
						strWidth += c;
					}
				}
				if (strWidth.length() == 0) {
					strWidth = "0";
				}
				tfWidth.setText(strWidth);
			}
			return;
			
		} else if (component == tfPw) {
			updateOutput();
			return;
		}
		switch (e.getKeyCode()) {
			case 27: { // ESC
				lvFiles.clearSelection();
				break;
			}
			case 127: { // Delete
				deleteSelected();
				break;
			}
			default: {
				logger.all("keyReleased: " + e.getKeyCode());
			}
		}
	}

	/**
	 * 프로그램 -> 탐색기 파일 드래그
	 * 
	 * @author harne_
	 *
	 */
    private class FileTransferHandler extends TransferHandler {
        @Override
        protected Transferable createTransferable(JComponent c) {
        	logger.debug("createTransferable");
			final List<File> rootFiles = new ArrayList<>();
			
        	if (c == lvFiles) { // 리스트의 파일 선택 드래그
        		List<Container> containers = lvFiles.getSelectedContainers();
        		if (containers.size() == 0) {
        			return null;
        		}
        		
        		// 임시 파일 생성
    			String rootDir = TMP_DIR + Calendar.getInstance().getTimeInMillis();
    			List<File> files = Container.containersToFiles(containers, rootDir);
    			
    			// 하위 디렉토리가 있는 경우 최상위 경로만 선택
    			List<String> primaryNames = new ArrayList<>();
    			for (File subFile : files) {
    				// 최상위 경로 찾기
    				String primaryName = subFile.getAbsolutePath().substring(rootDir.length() + 1).replace('\\', '/');
    				int index = primaryName.indexOf('/');
					if (index > 0) {
						primaryName = primaryName.substring(0, index);
					}
					if (!primaryNames.contains(primaryName)) {
						primaryNames.add(primaryName);
					}
    			}
    			
    			// 최상위 객체 리스트 전달
    			for (String name : primaryNames) {
    				rootFiles.add(new File(rootDir, name));
    			}
    			
        	} else if (c == ivOutput || c == ivTarget) { // 이미지뷰 드래그
        		BufferedImage image = targetImage;
        		String name = "target.png";
        		if (c == ivOutput) {
        			image = outputImage;
        			if (pngFile != null) {
        				name = pngFile.getName();
        			} else {
        				name = Calendar.getInstance().getTimeInMillis() + ".png";
        			}
        		}
        		
        		// 임시 PNG 파일 생성해서 전달
    			File file = new File(TMP_DIR + name);
    			file.deleteOnExit();
    			try {
    				ImageIO.write(image, "PNG", file); // 생각보다 PNG write 딜레이가 거슬림...
    				rootFiles.add(file);
    				
    			} catch (Exception e) {
    				logger.error("임시 파일 생성 실패");
    				logger.debug(e);
    			}
        	}
        	
        	if (rootFiles.size() > 0) {
        		return new Transferable() {
					@Override
					public DataFlavor[] getTransferDataFlavors() {
		        		return new DataFlavor[] { DataFlavor.javaFileListFlavor };
					}
					@Override
					public boolean isDataFlavorSupported(DataFlavor flavor) {
		        		return flavor.equals(DataFlavor.javaFileListFlavor);
					}
					@Override
					public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
		        		if (!isDataFlavorSupported(flavor)) {
		        			throw new UnsupportedFlavorException(flavor);
		        		}
		        		return rootFiles;
					}
				};
        	}
        	return null;
        }

        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }
    }
    
    /**
     * 이미지 -> 탐색기 드래그
     * 
     * @author harne_
     *
     */
	private static class ImageDragAdaptor extends MouseAdapter {
		private JComponent c;
		private FileTransferHandler fth;
		public ImageDragAdaptor(JComponent c, FileTransferHandler fth) {
			this.c = c;
			this.fth = fth;
		}
		
		@Override
		public void mousePressed(MouseEvent e) {
			logger.debug("ImageDragAdaptor.mousePressed");
			fth.exportAsDrag(c, e, TransferHandler.MOVE);
		}
		@Override
		public void mouseMoved(MouseEvent e) {
			logger.debug("ImageDragAdaptor.mouseMoved");
		}
		@Override
		public void mouseDragged(MouseEvent e) {
			logger.debug("ImageDragAdaptor.mouseDragged");
		}
		@Override
		public void mouseClicked(MouseEvent e) {
			logger.debug("ImageDragAdaptor.mouseClicked");
		}
		@Override
		public void mouseReleased(MouseEvent e) {
			logger.debug("ImageDragAdaptor.mouseReleased");
		}
	}
    
	/**
	 * 탐색기 등 외부 -> 프로그램 파일 드래그
	 * 
	 * @author harne_
	 *
	 */
    private static class FileDropTarget extends DropTarget {
		private static Border dragBorder = BorderFactory.createMatteBorder(2, 2, 2, 2, new Color(0f, 0f, 1f, 0.25f));
    	private Border normalBorder;
    	private GUI gui;
    	protected JComponent c;
    	
    	/**
    	 * 생성자에서 객체의 원래 테두리를 기억해둠
    	 * @param gui
    	 * @param c
    	 */
    	public FileDropTarget(GUI gui, JComponent c) {
    		this.gui = gui;
			normalBorder = (this.c = c).getBorder();
		}
    	
    	/**
    	 * 드래그 들어왔을 때 테두리 표현
    	 */
    	@Override
    	public synchronized void dragEnter(DropTargetDragEvent evt) {
    		logger.debug("FileDropTarget.dragEnter");
			normalBorder = c.getBorder();
			c.setBorder(dragBorder);
    	}
    	
		@SuppressWarnings("unchecked")
		@Override
		public synchronized void drop(DropTargetDropEvent evt) {
    		logger.debug("FileDropTarget.drop");
			try {
				Transferable tr = evt.getTransferable();

				logger.debug("allHtmlFlavor      : " + tr.isDataFlavorSupported(DataFlavor.allHtmlFlavor      ));
				logger.debug("fragmentHtmlFlavor : " + tr.isDataFlavorSupported(DataFlavor.fragmentHtmlFlavor ));
				logger.debug("imageFlavor        : " + tr.isDataFlavorSupported(DataFlavor.imageFlavor        ));
				logger.debug("javaFileListFlavor : " + tr.isDataFlavorSupported(DataFlavor.javaFileListFlavor ));
				logger.debug("selectionHtmlFlavor: " + tr.isDataFlavorSupported(DataFlavor.selectionHtmlFlavor));
				logger.debug("stringFlavor       : " + tr.isDataFlavorSupported(DataFlavor.stringFlavor       ));

				if (tr.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
					logger.debug("DataFlavor supports javaFileListFlavor");
					evt.acceptDrop(DnDConstants.ACTION_COPY);
					gui.dropFiles((List<File>) tr.getTransferData(DataFlavor.javaFileListFlavor), c);
					evt.getDropTargetContext().dropComplete(true);
					logger.debug("dropComplete");
					
				} else if (tr.isDataFlavorSupported(DataFlavor.imageFlavor)) {
					logger.debug("DataFlavor supports imageFlavor");
					evt.acceptDrop(DnDConstants.ACTION_COPY);
					try {
						Object data = tr.getTransferData(DataFlavor.imageFlavor);
						if (data instanceof AbstractMultiResolutionImage) {
							AbstractMultiResolutionImage mrci = (AbstractMultiResolutionImage) data;
							for (Image img : mrci.getResolutionVariants()) {
								if (img instanceof BufferedImage) {
									gui.openBitmap((BufferedImage) img);
									evt.getDropTargetContext().dropComplete(true);
									logger.debug("dropComplete");
									break;
								}
							}
						}
						
					} catch (Exception e) {
						logger.debug(e);
					}
					logger.warn("이미지 가져오기 실패");
					
				} else {
					logger.debug("DataFlavor don't support javaFileListFlavor and imageFlavor");
					
					DataFlavor[] flavors = tr.getTransferDataFlavors();
					boolean handled = false;

					for (DataFlavor flavor : flavors) {
						logger.debug("MimeType: " + flavor.getMimeType());
					}
					
					for (DataFlavor flavor : flavors) {
						logger.debug("flavor: " + flavor);
						if (flavor.isRepresentationClassReader()) {
							// 해석 가능한 객체를 찾음
							logger.debug("This flavor is RepresentationClassReader");
							evt.acceptDrop(DnDConstants.ACTION_COPY);
							
							Reader reader = flavor.getReaderForText(tr);
							logger.debug("reader: " + reader);
							
							BufferedReader br = new BufferedReader(reader);
							logger.debug("br: " + br);

							// BufferedReader로 가져오기 시도
							logger.info("BufferedReader로 가져오기 시도");
							List<File> fileList = createFileList(br);
							logger.debug("fileList.size: " + fileList.size());
							if (fileList.size() > 0) {
								gui.dropFiles(fileList, c);
								evt.getDropTargetContext().dropComplete(true);
								logger.debug("dropComplete");
								handled = true;
								break;
							}
							
							// BufferedReader로 가져오기 실패
							logger.info("BufferedReader로 가져오기 실패");
						}
					}
					if (!handled) {
						logger.debug("해석 가능한 객체를 찾지 못함");
						evt.rejectDrop();
					}
				}
			} catch (IOException e) {
				logger.warn("잘못된 드래그 객체");
				logger.debug(e);
				evt.rejectDrop();
			} catch (UnsupportedFlavorException e) {
				logger.warn("잘못된 드래그 객체");
				logger.debug(e);
				evt.rejectDrop();
			} finally {
				// 드롭됐으므로 테두리 복원
				c.setBorder(normalBorder);
			}
		}
		
		/**
		 * 드래그 끝났을 때 테두리 복원
		 */
		@Override
		public synchronized void dragExit(DropTargetEvent evt) {
    		logger.debug("FileDropTarget.dragExit");
			c.setBorder(normalBorder);
		}

		/**
		 * DataFlavor에서 가져온 BufferedReader를 읽어 파일 목록 생성
		 * @param bReader
		 * @return 파일 목록
		 */
		private static List<File> createFileList(BufferedReader bReader) {
			logger.info("FileDropTarget.createFileList");
			List<File> list = new ArrayList<>();
			try {
				String line = null;
				while ((line = bReader.readLine()) != null) {
					if (line.startsWith("<")) { // Windows 웹페이지 드래그 시
						if (line.startsWith("<!--StartFragment-->")) {
							logger.debug("<!--StartFragment--> found");
							int index = line.indexOf(" src=");
							if (index > 0) {
								// 이미지 주소 가져오기
								String src = line.substring(index + 5, line.indexOf(">", index));
								char target = ' ';
								if (src.charAt(0) == '"') {
									src = src.substring(1);
									target = '"';
								} else if (src.charAt(0) == '\'') {
									src = src.substring(1);
									target = '\'';
								}
								index = src.indexOf(target);
								if (index > 0) {
									src = src.substring(0, index);
								}
								src = src.replace("&amp;", "&");
								
								// 이미지 주소에서 파일 가져오기
								File file = null;
								try {
									file = fileFromUrl(src);
								} catch (Exception e) {
									logger.debug(e);
								}
								if (file == null && src.startsWith("https://")) {
									try {
										logger.debug("https였을 경우 http로 재시도");
										file = fileFromUrl("http://" + src.substring(8));
									} catch (Exception e) {
										logger.debug(e);
									}
								}
								if (file == null) {
									logger.error("파일 가져오기 실패");
								} else {
									list.add(file);
								}
							}
						}
					} else {
						try {
							if ("".equals(line)) {
								continue;
							}
							logger.debug("Try " + line);
							list.add(new File(new URI(line)));
							
						} catch (Exception e) {
							logger.error("파일 가져오기 실패");
							logger.debug(e);
						}
					}
				}
				
			} catch (IOException e) {
				logger.error("드래그 객체 해석 실패");
				logger.debug(e);
			}
			return list;
		}
	};
	
	/**
	 * 탐색기 등 외부 -> 프로그램 파일 드래그
	 * @param files
	 * @param c
	 */
	private void dropFiles(List<File> files, JComponent c) {
		logger.info("dropFiles");
		do {
			// 파일 하나일 경우 이미지 열기 시도
			if (files.size() != 1) break;
			
			File file = files.get(0);
			
			if (c == panelExport) {
				// 추출할 폴더 선택
				tfExportDir.setText(file.isDirectory() ? file.getAbsolutePath() : file.getParent());
				
			} else {
				String name = file.getName();
				int index = name.lastIndexOf(".");
				if (index < 1) break; // 확장자 못 찾으면 무시
				String ext = name.substring(index + 1, name.length()).toLowerCase();
				
				if (c == panelPreview) {
					// 이미지 파일 열기
					if (!ext.equals("png")
					 && !ext.equals("bmp")
					 && !ext.equals("jpg")
					 && !ext.equals("gif")) {
						break;
					}
					if (file.getAbsolutePath().replace('\\', '/').equals(TMP_DIR + "target.png")) {
						// 입력 이미지 그대로 드래그함
						break;
					}
					
					if (!confirm(Strings.get("confirm set image"), Strings.get("open image"))) {
						return;
					}
					if (!setTargetImage(file.getAbsolutePath())) {
						alert("이미지를 적용할 수 없습니다.");
					}
					
				} else {
					// PNG 파일 열기
					if (!ext.equals("png")) {
						if (c == panelPngFile) {
							alert("해석할 수 없는 파일입니다.");
						}
						break;
					}
					
					if (!lvFiles.isEmpty()) {
						if (!confirm(Strings.get("confirm close file"), Strings.get("open png"))) {
							if (confirm(Strings.get("confirm add file"), Strings.get("open png"))) {
								c = panelFilesEdit;
								break;
							}
							return;
						}
					}
					if (!openPng(file.getAbsolutePath())) {
						alert(Strings.get("cant parse png and add to list"));
						break;
					}
				}
			}
			return;
			
		} while (false);
		
		// 파일 목록에 드래그한 경우: 파일 추가
		if (c == panelFilesEdit) {
			addFiles(files.toArray(new File[files.size()]));
		}
	}
	
	private void alert(String msg) {
		JOptionPane.showMessageDialog(this, msg);
	}
	private boolean confirm(String msg, String title) {
		return JOptionPane.showConfirmDialog(this, msg, title, JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
	}
	
	/**
	 * URL에서 파일 생성
	 * @param url
	 * @return
	 * @throws Exception
	 */
	private static File fileFromUrl(String strUrl) throws Exception {
		logger.info("fileFromUrl: " + strUrl);
		URL url = new URL(strUrl);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setConnectTimeout(2000);
		conn.setReadTimeout(2000);
		conn.setRequestMethod("GET");
		conn.getResponseCode();
		
		String contentLength = conn.getHeaderField("Content-Length");
		int size = Integer.parseInt(contentLength);
		if (size> 20971520) {
			throw new Exception("20MB를 초과합니다");
		}
		
		String contentDisposition = conn.getHeaderField("Content-Disposition");
		String filename = null;
		if (contentDisposition == null) {
			String path = url.getPath();
			filename = path.substring(path.lastIndexOf("/") + 1);
		} else {
			filename = URLDecoder.decode(contentDisposition.replaceFirst("(?i)^.*filename=\"?([^\"]+)\"?.*$", "$1"), "UTF-8");
		}
		
		// 확장자 없으면 PNG로 시도
		int index = filename.lastIndexOf(".");
		if (index > 0) {
			String ext = filename.substring(index);
			if (".php".contentEquals(ext)
			 || ".jsp".contentEquals(ext)
			 || ".asp".contentEquals(ext)) {
				filename = filename.substring(0, index) + ".png";
			}
		} else {
			filename += ".png";
		}
		File file = new File(TMP_DIR + filename);
		file.deleteOnExit();
		
		InputStream is = null;
		FileOutputStream fos = null;
		try {
			is = conn.getInputStream();
			fos = new FileOutputStream(file);
			
			byte[] buffer = new byte[4096];
			int length;
			while ((length = is.read(buffer)) > 0) {
				fos.write(buffer, 0, length);
			}
			
		} catch (Exception e) {
			logger.error("임시 URL 파일 생성 실패");
			logger.debug(e);
			
		} finally {
			try { if (is  != null) is .close(); } catch (Exception e) { }
			try { if (fos != null) fos.close(); } catch (Exception e) { }
		}
		
		return file;
	}
	
	public static void main(String[] args) {
		Container.setLogger(logger);
		
		GUI gui = new GUI();
		
		// 3번 인자: 바로 종료 여부
		boolean disposeAfterExport = (args.length > 2);
		if (!disposeAfterExport) {
			gui.init();
		}
		
		// 1번 인자: PNG 파일 경로
		if (args.length > 0) {
			// PNG 파일 열기
			gui.openPng(args[0]);
			
			// 2번 인자: 추출 경로
			if (args.length > 1) {
				// 전체 선택
				int[] indices = new int[gui.lvFiles.count()];
				for (int i = 0; i < indices.length; i++) {
					indices[i] = i;
				}
				gui.lvFiles.setSelectedIndices(indices);
				
				if (disposeAfterExport) {
					// 추출 후 메시지 없이 종료
					gui.exportSelected(args[1], false);
					gui.dispose();
				} else {
					// 추출
					gui.exportSelected(args[1], true);
				}
			}
		}
	}
}