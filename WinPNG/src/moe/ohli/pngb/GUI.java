package moe.ohli.pngb;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.*;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.filechooser.FileNameExtensionFilter;

import moe.ohli.pngb.Explorer.*;
import sun.awt.image.AbstractMultiResolutionImage;

@SuppressWarnings("serial")
public class GUI extends JFrame implements ActionListener, KeyListener, Explorer.Listener {
	
	private static final String TMP_DIR = (System.getProperty("java.io.tmpdir").replace('\\', '/') + "/WinPNG/").replace("//", "/");
	private static final String CONFIG_FILE_PATH = TMP_DIR + "config.properties";
	private static final BufferedImage JUNK_IMAGE = new BufferedImage(16, 9, BufferedImage.TYPE_3BYTE_BGR);
	static {
		Graphics graphics = JUNK_IMAGE.getGraphics();
		graphics.setColor(Color.WHITE);
		graphics.fillRect(0, 0, 16, 9);
		graphics.dispose();
	}
	private static final Color BORDER_COLOR = new Color(204, 204, 204);
	private static final Border DRAG_BORDER = BorderFactory.createMatteBorder(1, 1, 1, 1, new Color(0, 120, 215));
	
	private static Logger logger = new Logger(Logger.L.DEBUG); // 로그 파일 로깅 수준 기본값 디버그

	private static String strSize(int size) {
		String strSize = size + "Bytes";
		if (size > 10240) { // 10.0kB 이상
			strSize = "" + ((size * 10 + 512) / 1024);
			strSize = strSize.substring(0, strSize.length() - 1) + "." + strSize.substring(strSize.length() - 1) + "kB";
		} else if (size > 1000) { // 1,000B 이상
			strSize = (size / 1000) + "," + (size % 1000) + "Bytes";
		}
		return strSize;
	}
	
	private File pngFile = null;
	private BufferedImage openedImage = null;
	private BufferedImage targetImage = null;
	private BufferedImage outputImage = null;
	
	private Properties props = new Properties();
	
    /**
     * 기본 스타일 및 리스너 적용된 버튼
     * 
     * @author harne_
     *
     */
    private static class MyButton extends JButton {
    	private static final Border BTN_MARGIN = new EmptyBorder(4, 8, 4, 8);
    	private static final Border BTN_BORDER = BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, BORDER_COLOR), BTN_MARGIN);
    	public MyButton(GUI gui) {
    		super();
    		init(gui);
    	}
    	private static Font font;
    	private void init(GUI gui) {
    		if (font == null) {
    			@SuppressWarnings("unchecked")
				Map<TextAttribute, Object> attributes = (Map<TextAttribute, Object>) getFont().getAttributes();
    			attributes.put(TextAttribute.WEIGHT, TextAttribute.WEIGHT_REGULAR);
    			font = new Font(attributes);
    		}
    		setBorder(isWindows ? BTN_MARGIN : BTN_BORDER);
    		addActionListener(gui);
    		addKeyListener(gui);
    	}
    }
	
    /**
     * 기본 여백 라벨
     * 
     * @author harne_
     *
     */
	private static class MyLabel extends JLabel {
		private static final EmptyBorder BORDER = new EmptyBorder(2, 8, 2, 8);
		
		public MyLabel() {
			super();
			init();
		}
		public MyLabel(String text) {
			super(text);
			init();
		}
		public MyLabel(String text, int align) {
			super(text, align);
			init();
		}
		private void init() {
			setBorder(BORDER);
		}
		
		public static MyLabel withoutBorder() {
			return withoutBorder("");
		}
		public static MyLabel withoutBorder(String text) {
			MyLabel label = new MyLabel(text);
			label.setBorder(null);
			return label;
		}
	}
	
	/**
	 * 리스너 적용된 메뉴 아이템
	 * 
	 * @author harne_
	 *
	 */
	private class MyMenuItem extends JMenuItem {
		private ActionListener acMenu = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				Object source = e.getSource();
				if (source == miOpenFile) {
					explorer.openSelectedFile();
				} else if (source == miRename) {
					explorer.renameSelected();
				} else if (source == miRemove) {
					explorer.removeSelected();
				} else if (source == miCopyFiles) {
					Transferable contents = explorer.createTransferable();
					Toolkit.getDefaultToolkit().getSystemClipboard().setContents(contents, null);
				} else if (source == miAddFile) {
					addFileWithDialog();
				} else if (source == miSelectAll) {
					explorer.selectAll();
				} else if (source == miIfError) {
					requestCheckError();
				}
			}
		};
		public MyMenuItem() {
			super();
			addActionListener(acMenu);
		}
	}
	private JMenuItem miOpenFile  = new MyMenuItem();
	private JMenuItem miRename    = new MyMenuItem();
	private JMenuItem miRemove    = new MyMenuItem();
	private JMenuItem miCopyFiles = new MyMenuItem();
	private JMenuItem miAddFile   = new MyMenuItem();
	private JMenuItem miSelectAll = new MyMenuItem();
	private JMenuItem miIfError   = new MyMenuItem();
	
	/**
	 * 우클릭 리스너
	 * 
	 * @author harne_
	 *
	 */
	private class ImageActionListener implements ActionListener {
		private Transferable tr;
		private Component c;
		
		public void setAction(Transferable tr, Component c) {
			this.tr = tr;
			this.c = c;
		}
		
		@Override
		public void actionPerformed(ActionEvent e) {
			if (e.getSource() == miCopyImage) {
				if (c == ivTarget) {
					copyToClipboard(targetImage);
				} else if (c == ivOutput) {
					copyToClipboard(outputImage);
				}
			} else {
				pasteFromClipboard(tr, c, true);
			}
		}
	}
	private ImageActionListener ial = new ImageActionListener();
	private JMenuItem miPasteText     = new JMenuItem();
	private JMenuItem miCopyImage = new JMenuItem();
	
	// 윗줄 PNG 파일 읽기
	private JPanel panelPngFile = new JPanel(new BorderLayout());
	private JTextField tfPngFile = new JTextField();
	private JButton btnOpen = new MyButton(this), btnClose = new MyButton(this);
	
	// 좌측 내용물
	private JPanel panelFilesEdit = new JPanel(new BorderLayout());
	private Explorer explorer = new Explorer(logger, this, ".png");
	private JLabel labelStatus = new MyLabel("", SwingConstants.LEFT);
	private JLabel labelInfo = new MyLabel("", SwingConstants.RIGHT);
	private JPanel panelExport = new JPanel(new BorderLayout());
	private JTextField tfExportDir = new JTextField();
	private JButton btnExport = new MyButton(this);
	
	// 우측 이미지
	private JPanel panelRight  = new JPanel(new BorderLayout()), panelPreview = new JPanel()
	             , panelTarget = new JPanel(new BorderLayout()), panelRatio   = new JPanel()
	             , panelOutput = new JPanel(new BorderLayout());
	private JLabel ivTarget = MyLabel.withoutBorder(), jlTarget = new MyLabel(), jlRatio = MyLabel.withoutBorder()
	             , ivOutput = MyLabel.withoutBorder(), jlOutput = new MyLabel(), jlPw = MyLabel.withoutBorder(), jlWidth = MyLabel.withoutBorder();
	private JRadioButton rbTarget114 = new JRadioButton();
	private JRadioButton rbTarget238 = new JRadioButton();
	private JRadioButton rbTarget124 = new JRadioButton();
	private JRadioButton rbTarget011 = new JRadioButton();
	private ButtonGroup rbGroupTarget = new ButtonGroup();
	private JTextField tfRatioW = new JTextField("16"), tfRatioH = new JTextField("9"), tfPw = new JTextField(""), tfWidth = new JTextField("0");
	private JButton btnSave = new MyButton(this), btnCopy = new MyButton(this);

	private static final int IMAGE_VIEW_WIDTH = 280, IMAGE_VIEW_HEIGHT = 158;
	
	private static final String OS = System.getProperty("os.name");
	private static boolean USE_JFC = false;
	private static boolean isWindows = OS.toLowerCase().startsWith("windows");
	private static boolean isLinux   = OS.toLowerCase().startsWith("linux");
	private static boolean isMac     = OS.toLowerCase().startsWith("mac");
	private boolean isAndroid = false;
	
	private JFileChooser fcPng = new JFileChooser();
	
	public void init() {
		setTitle("WinPNG");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		isAndroid = isLinux && (screenSize.width < 800);
		
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
				try {
					if (isAndroid) {
						File logFile = new File("/storage/emulated/0/Download/WinPNG/WinPNG_log.txt");
						logger.set(new PrintStream(logFile)); // 로그 파일 로깅 수준은 설정값에 따름
						
					} else {
						String logPath = TMP_DIR + "log";
						File logDir = new File(logPath);
						logDir.mkdirs();
						System.out.println("로그 파일 위치: " + logPath);

						File logFile = new File(logPath + "/" + Calendar.getInstance().getTimeInMillis() + ".log");
						logger.set(new PrintStream(logFile)); // 로그 파일 로깅 수준은 설정값에 따름
						
						logFile = new File(logPath + "/" + Calendar.getInstance().getTimeInMillis() + ".info.log");
						logger.set(new PrintStream(logFile), Logger.L.INFO); // INFO 로그 파일도 남김
					}
				} catch (FileNotFoundException e) {
					System.out.println("로그 파일 설정 실패");
					e.printStackTrace();
				}
			}
			/*
			{	// 이런 식으로 단계별 로그를 동시에 만들 수 있음
				File logDir = new File(TMP_DIR + "log");
				logDir.mkdirs();
				String logPath = TMP_DIR + "log/" + Calendar.getInstance().getTimeInMillis();
				File logFileDebug = new File(logPath + "_debug.log");
				File logFileInfo  = new File(logPath + "_info_.log");
				File logFileError = new File(logPath + "_error.log");
				try {
					logger.set(new PrintStream(logFileDebug), Logger.L.DEBUG);
					logger.set(new PrintStream(logFileInfo ), Logger.L.INFO );
					logger.set(new PrintStream(logFileError), Logger.L.ERROR);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			*/
			try {
				String logLevel = props.getProperty("LogLevel");
				logger.setDefaultLevel(Logger.L.valueOf(logLevel)); // 로그 파일 로깅 수준은 설정값에 따름
				logger.log(Logger.L.INFO, "로그 레벨: " + logLevel);
			} catch (Exception e) {
				logger.log(Logger.L.ERROR, "로그 레벨 설정 가져오기 실패");
				logger.log(Logger.L.DEBUG, e); // 기본 로깅 수준은 DEBUG
			}
			logger.set(System.out, Logger.L.INFO); // 로그 파일과 별개로 콘솔에는 INFO로 찍음
			
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
				int width  = Math.min(800, screenSize.width );
				int height = Math.min(600, screenSize.height);
				setBounds((screenSize.width - width) / 2, (screenSize.height - height) / 2, width, height);
			}
			setMinimumSize(new Dimension(600, 550));
			
			// 기타 설정
			try {
				String useTargetImage = props.getProperty("useTargetImage");
				if       ( "114v2".equals(useTargetImage)
						|| "114v3".equals(useTargetImage)
						|| "114".equals(useTargetImage)) {
					rbTarget114.setSelected(true);
					tfRatioW.setEditable(false);
					tfRatioH.setEditable(false);
				} else if ("238".equals(useTargetImage)) {
					rbTarget238.setSelected(true);
					tfRatioW.setEditable(false);
					tfRatioH.setEditable(false);
				} else if ("149".equals(useTargetImage)
				        || "124".equals(useTargetImage)) {
					rbTarget124.setSelected(true);
					tfRatioW.setEditable(false);
					tfRatioH.setEditable(false);
				} else {
					rbTarget011.setSelected(true);
				}
			} catch (Exception e) {
				logger.warn("이미지 입력 설정 가져오기 실패");
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
			try {
				explorer.setDirWidth(Integer.parseInt(props.getProperty("dirWidth")));
			} catch (Exception e) {
				logger.warn("디렉토리 트리 크기 가져오기 실패");
				logger.debug(e);
			}
			
			if (isAndroid) {
				// Android JRE 실행을 가정
				int width  = screenSize.width;
				int height = 550;
				setBounds(0, (screenSize.height - height) / 2, width, height);
				
				// 다운로드 폴더 내 WinPNG를 기본 추출 폴더로 설정
				exportDir = "/storage/emulated/0/Download/WinPNG";
				
				// OS 자체 파일 선택기 사용 불가능하므로
				USE_JFC = true; // ... 어차피 안드로이드 권한 문제로 파일 못 가져옴
				
			} else {
				String language = props.getProperty("language");
				if (language == null || language.length() == 0) {
					// 설정 없으면 시스템 언어 가져오기
					language = Locale.getDefault().getLanguage();
					props.setProperty("language", language);
				}
				if ("ko".equals(language)) {
					Strings.setLanguage(Strings.Language.KR);
				} else if ("jp".equals(language)) {
					Strings.setLanguage(Strings.Language.JP);
				}
			}
		}
		
		{	// 문자열 설정
			if (!isAndroid) {
				tfPngFile.setText(Strings.get("이미지 파일을 드래그해서 열 수 있습니다."));
			}
			btnOpen .setText(Strings.get("열기"));
			btnClose.setText(Strings.get("닫기"));
			
			if (exportDir != null && exportDir.length() > 0) {
				tfExportDir.setText(exportDir);
			} else {
				tfExportDir.setText(Strings.get("목록에서 파일 선택 후 탐색기로 드래그, 혹은 파일을 추출할 경로 입력"));
			}
			btnExport.setText(Strings.get("추출"));
			
			ivTarget.setToolTipText(Strings.get("Ctrl+V로 이미지를 적용할 수 있습니다."));
			ivOutput.setToolTipText(Strings.get("Ctrl+C로 복사할 수 있습니다."));
			jlTarget.setText(Strings.get("입력 이미지"));
			rbTarget114.setText("1:1:4");
			rbTarget238.setText("2:3:8");
			rbTarget124.setText("1:2:4");
			rbTarget011.setText(isWindows ? Strings.get("사용 안 함") : "X");
			jlRatio.setText("  " + Strings.get("비율") + ": ");
			jlOutput.setText(Strings.get("출력 이미지"));
			jlPw.setText(Strings.get("비밀번호 걸기") + " ");
			jlWidth.setText(Strings.get("최소 폭") + " ");
			btnSave.setText(Strings.get("이미지 저장"));
			btnCopy.setText(Strings.get("이미지 복사"));
			
			miOpenFile .setText(Strings.get("열기"       ));
			miRename   .setText(Strings.get("이름 바꾸기")); 
			miRemove   .setText(Strings.get("파일 삭제"  ));
			miCopyFiles.setText(Strings.get("파일 복사"  ));
			miAddFile  .setText(Strings.get("파일 추가"  ));
			miSelectAll.setText(Strings.get("전체 선택"  ));
			miIfError  .setText(Strings.get("해석 오류"  ));
			
			miPasteText.setText(Strings.get("붙여넣기"));
			miCopyImage .setText(Strings.get("이미지 복사"));
		}
		
		{	// 레이아웃 구성
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
					panelFilesEdit.add(explorer, BorderLayout.CENTER);
					JPanel panelStatus = new JPanel(new BorderLayout());
					panelStatus.add(labelStatus, BorderLayout.WEST);
					panelStatus.add(labelInfo, BorderLayout.EAST);
					panelFilesEdit.add(panelStatus , BorderLayout.SOUTH);
					panelFiles.add(panelFilesEdit, BorderLayout.CENTER);
					explorer.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 1, BORDER_COLOR));
				}
				{	// 버튼 영역
					panelExport.add(tfExportDir, BorderLayout.CENTER);
					panelExport.add(btnExport  , BorderLayout.EAST);
					panelFiles.add(panelExport, BorderLayout.SOUTH);
				}
				add(panelFiles, BorderLayout.CENTER);
			}
			{	// 우측 이미지
				{	// 이미지뷰 영역
					panelPreview.setLayout(new BoxLayout(panelPreview, BoxLayout.Y_AXIS));
					panelPreview.add(new JPanel());
					{	// 입력 이미지
						JPanel panelRadio = new JPanel();
						panelTarget.add(jlTarget, BorderLayout.NORTH);
						panelTarget.add(ivTarget, BorderLayout.CENTER);
						panelRadio.add(rbTarget114);
						panelRadio.add(rbTarget238);
						panelRadio.add(rbTarget124);
						panelRadio.add(rbTarget011);
						rbGroupTarget.add(rbTarget114);
						rbGroupTarget.add(rbTarget238);
						rbGroupTarget.add(rbTarget124);
						rbGroupTarget.add(rbTarget011);
						panelTarget.add(panelRadio, BorderLayout.SOUTH);
						panelPreview.add(panelTarget);
					}
					panelPreview.add(new JPanel());
					{	// 출력 크기
						panelRatio.add(jlWidth);
						panelRatio.add(tfWidth);
						tfWidth.setColumns(4);
						
						panelRatio.add(jlRatio);
						panelRatio.add(tfRatioW);
						panelRatio.add(MyLabel.withoutBorder(":"));
						panelRatio.add(tfRatioH);
						tfRatioW.setColumns(3);
						tfRatioH.setColumns(3);
						
						panelPreview.add(panelRatio);
					}
					{	// 출력 이미지
						panelOutput.add(jlOutput, BorderLayout.NORTH);
						panelOutput.add(ivOutput, BorderLayout.CENTER);
						panelPreview.add(panelOutput);
					}
					{	// 출력 비밀번호
						JPanel panelPw = new JPanel(new BorderLayout());
						panelPw.add(jlPw, BorderLayout.WEST);
						panelPw.add(tfPw, BorderLayout.CENTER);
						panelOutput.add(panelPw, BorderLayout.SOUTH);
					}
					panelPreview.add(new JPanel());
					panelRight.add(panelPreview, BorderLayout.CENTER);
					
					panelPreview.setBorder(null);
					panelRight.setBorder(null);
				}
				{	// 버튼 영역
					JPanel panelSave = new JPanel(new BorderLayout());
					
					JPanel panelSaveBtn = new JPanel(new GridLayout(1, 2));
					panelSaveBtn.add(btnCopy);
					panelSaveBtn.add(btnSave);
					panelSave.add(panelSaveBtn, BorderLayout.EAST);
					
					panelRight.add(panelSave, BorderLayout.SOUTH);
				}
				{	// 우클릭 메뉴 단축키
					miOpenFile .setAccelerator(KeyStroke.getKeyStroke("O"));
					miRename   .setAccelerator(KeyStroke.getKeyStroke("R")); 
					miRemove   .setAccelerator(KeyStroke.getKeyStroke("D"));
					miCopyFiles.setAccelerator(KeyStroke.getKeyStroke("C"));
					miAddFile  .setAccelerator(KeyStroke.getKeyStroke("N"));
					miSelectAll.setAccelerator(KeyStroke.getKeyStroke("A"));
					miIfError  .setAccelerator(KeyStroke.getKeyStroke("E"));
				}
				
				panelTarget.setMaximumSize(new Dimension(IMAGE_VIEW_WIDTH, IMAGE_VIEW_HEIGHT + 40));
				panelOutput.setMaximumSize(new Dimension(IMAGE_VIEW_WIDTH, IMAGE_VIEW_HEIGHT + 20));
				panelRight.setPreferredSize(new Dimension(IMAGE_VIEW_WIDTH, 0));
				
				updateTarget(JUNK_IMAGE);
				updateOutput();
				
				if (isAndroid) {
					JTextArea taConsole = new JTextArea(50, 10);
					JScrollPane spConsole = new JScrollPane(taConsole);
					add(spConsole, BorderLayout.EAST);
					spConsole.setPreferredSize(new Dimension(IMAGE_VIEW_WIDTH, 400));
					
					logger.set(new PrintStream(new OutputStream() {
						@Override
						public void write(int b) throws IOException {
							taConsole.append(String.valueOf((char) b));
							taConsole.setCaretPosition(taConsole.getDocument().getLength());
							taConsole.update(taConsole.getGraphics());
						}
					}), Logger.L.INFO);
					
				} else {
					add(panelRight, BorderLayout.EAST);
				}
			}
		}
		
		{	// 이벤트 설정
			for (Component c : new Component[] { this, explorer, tfRatioW, tfRatioH, tfPw, tfWidth, tfPngFile, rbTarget114, rbTarget238, rbTarget124, rbTarget011 }) {
				c.addKeyListener(this);
			}
			for (AbstractButton c : new AbstractButton[] { rbTarget114, rbTarget238, rbTarget124, rbTarget011 }) {
				c.addActionListener(this);
			}
			
			FileTransferHandler fth = new FileTransferHandler();
			ivTarget.setTransferHandler(fth);
			ivOutput.setTransferHandler(fth);
			ivTarget.addMouseListener(new ImageDragAdaptor(ivTarget, fth));
			ivOutput.addMouseListener(new ImageDragAdaptor(ivOutput, fth));
			
			panelPngFile  .setDropTarget(new FileDropTarget(this, panelPngFile  ));
			tfPngFile     .setDropTarget(new FileDropTarget(this, panelPngFile  ));
			explorer      .setDropTarget(new FileDropTarget(this, panelFilesEdit));
			panelFilesEdit.setDropTarget(new FileDropTarget(this, panelFilesEdit));
			panelExport   .setDropTarget(new FileDropTarget(this, panelExport   ));
			tfExportDir   .setDropTarget(new FileDropTarget(this, panelExport   ));
			panelTarget   .setDropTarget(new FileDropTarget(this, panelTarget   ));
			ivTarget      .setDropTarget(new FileDropTarget(this, panelTarget   ));
			
			{	// 우클릭 이미지 붙여넣기
				miPasteText.addActionListener(ial);
				miCopyImage.addActionListener(ial);
				miPasteText.setAccelerator(KeyStroke.getKeyStroke("P"));
				miCopyImage.setAccelerator(KeyStroke.getKeyStroke("C"));
				
				MouseAdapter mouseRightAdapter = new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent evt) {
						int button = evt.getButton();
						logger.info("mouseClicked: " + button);
						if (button == MouseEvent.BUTTON3 || button == 0) { // Android JRE의 RMB가 0이 나옴
							try {
								Component c = evt.getComponent();
								Transferable tr = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
								ial.setAction(tr, c);
								
								JPopupMenu menu = new JPopupMenu();
								if (c != tfPngFile) {
									menu.add(miCopyImage);
								}
								if (c != ivOutput) {
									miPasteText.setEnabled(tr.isDataFlavorSupported(DataFlavor.stringFlavor)
									                    || tr.isDataFlavorSupported(DataFlavor.imageFlavor)
									                    || tr.isDataFlavorSupported(DataFlavor.javaFileListFlavor));
									menu.add(miPasteText);
								}
								
								menu.show(c, evt.getX(), evt.getY());
								
							} catch (Exception e) {
								logger.error(e);
							}
						}
					}
				};
				tfPngFile  .addMouseListener(mouseRightAdapter);
				panelTarget.addMouseListener(mouseRightAdapter);
				ivTarget   .addMouseListener(mouseRightAdapter);
				ivOutput   .addMouseListener(mouseRightAdapter);
			}
			
			// 종료 이벤트 시 설정 저장
			addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent evt) {
					super.windowClosing(evt);

					saveConfig();
					
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
			fcPng.setFileFilter(new FileNameExtensionFilter(Strings.get("PNG 파일(*.png)"), "png"));
			fcPng.setFileSelectionMode(JFileChooser.FILES_ONLY);
			fcPng.setMultiSelectionEnabled(false);
		}
		
		// 아이콘 가져오기
		setIconImage(new ImageIcon(getClass().getResource("icon.png")).getImage());
		
		// 창 띄우기
		setVisible(true);
		setEnabled(true);
	}
	/**
	 * 설정 저장
	 */
	private void saveConfig() {
		Rectangle bounds = getBounds();
		props.setProperty("LogLevel", logger.getDefaultLevel().name());
		props.setProperty("bounds", bounds.x + "," + bounds.y + "," + bounds.width + "," + bounds.height);
		props.setProperty("dirWidth", explorer.getDirWidth() + "");
		props.setProperty("minWidth", tfWidth.getText());
		props.setProperty("exportDir", tfExportDir.getText());
		if        (rbTarget114.isSelected()) {
			props.setProperty("useTargetImage", "114");
		} else if (rbTarget238.isSelected()) {
			props.setProperty("useTargetImage", "238");
		} else if (rbTarget124.isSelected()) {
			props.setProperty("useTargetImage", "124");
		} else if (rbTarget011.isSelected()) {
			props.setProperty("useTargetImage", "011");
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
	
	private void updateInfo() {
		List<Container> conts = explorer.getAllContainers();
		int size = 0;
		for (Container cont : conts) {
			size += cont.binary.length;
		}
		labelInfo.setText(Strings.get("파일 {count}개 / {size}").replace("{count}", ""+conts.size()).replace("{size}", strSize(size)));
	}
	
	private static ImageIcon makeImageIcon(BufferedImage image) {
		// 비율을 유지한 채로 조절한 크기를 구함
		int w = IMAGE_VIEW_WIDTH, h = IMAGE_VIEW_HEIGHT;
		double ratioW = (double) w / image.getWidth();
		double ratioH = (double) h / image.getHeight();
		if (ratioW < ratioH) {
			h = (int) Math.round(ratioW * image.getHeight());
		} else {
			w = (int) Math.round(ratioH * image.getWidth());
		}
		
		// 뷰 사이즈에 맞춰서 여백이 있는 이미지를 생성
		BufferedImage resized = new BufferedImage(IMAGE_VIEW_WIDTH, IMAGE_VIEW_HEIGHT, BufferedImage.TYPE_3BYTE_BGR);
		Graphics graphics = resized.getGraphics();
		graphics.setColor(Color.LIGHT_GRAY);
		graphics.fillRect(0, 0, IMAGE_VIEW_WIDTH, IMAGE_VIEW_HEIGHT);
		graphics.drawImage(image.getScaledInstance(w, h, Image.SCALE_SMOOTH), (IMAGE_VIEW_WIDTH - w) / 2, (IMAGE_VIEW_HEIGHT - h) / 2, null);
		graphics.dispose();
		
		return new ImageIcon(resized);
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
			updateStatus = 1;
			updateInfo();
			
			if (explorer.isEmpty()) {
				outputImage = null;
				ivOutput.setIcon(makeImageIcon(JUNK_IMAGE));
				jlOutput.setText(Strings.get("출력 이미지"));
				break;
			}
			
			int inputMinWidth = 0;
			try {
				inputMinWidth = Integer.parseInt(tfWidth.getText().trim());
			} catch (Exception e) {
				logger.warn("올바른 크기를 입력하세요.");
				logger.debug(e);
				alert(Strings.get("올바른 크기를 입력하세요."));
				break;
			}
			
			final int minWidth = inputMinWidth;
			if (minWidth > 1920) {
				logger.warn("최소 크기가 너무 큽니다.");
				alert(Strings.get("최소 크기가 너무 큽니다.\n1920 이하의 값을 적어주세요."));
				break;
			}
			
			// 별도 스레드에서 진행
			new Thread(new Runnable() {
				@Override
				public void run() {
					List<Container> containers = explorer.getAllContainers();
					try {
						String password = tfPw.getText();
						if        (rbTarget114.isSelected()) {
							outputImage = new Container.WithTarget(targetImage, containers).toBitmap114(minWidth, password);
						} else if (rbTarget238.isSelected()) {
							outputImage = new Container.WithTarget(targetImage, containers).toBitmap238(minWidth, password);
						} else if (rbTarget124.isSelected()) {
							outputImage = new Container.WithTarget(targetImage, containers).toBitmap124(minWidth, password);
						} else {
							int w = Integer.parseInt(tfRatioW.getText().trim());
							int h = Integer.parseInt(tfRatioH.getText().trim());
							// 이미지와 결합하지 않을 경우 섞어서 난수화
							Collections.shuffle(containers);
							outputImage = Container.toBitmapTwice(containers, minWidth, (h / (double) w), password);
						}
						ivOutput.setIcon(makeImageIcon(outputImage));
						jlOutput.setText(Strings.get("출력 이미지") + "(" + outputImage.getWidth() + "×" + outputImage.getHeight() + ")");
						
					} catch (Exception e) {
						logger.error("이미지 생성 실패");
						logger.debug(e);
						alert("이미지 생성 실패\n" + e.getMessage());
					}
					
					if (--updateStatus > 0) {
						logger.info("이미지 생성 대기열 실행");
						updateStatus = 0;
						updateOutput();
					}
				}
			}).start();
			return;
		} while (false);
		
		// 이미지 생성 로직 안 돌면 바로 이쪽으로 옴
		if (--updateStatus > 0) {
			logger.info("이미지 생성 대기열 실행");
			updateStatus = 0;
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
		ivTarget.setIcon(makeImageIcon(targetImage = image));
		if (image != null) {
			jlTarget.setText(Strings.get("입력 이미지") + "(" + image.getWidth() + "×" + image.getHeight() + ")");
			if (!rbTarget011.isSelected()) {
				tfRatioW.setText(""+image.getWidth());
				tfRatioH.setText(""+image.getHeight());
			}
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
					alert(Strings.get("해석할 수 없는 파일입니다."));
				}
			}
		} else {
			if (fdOpen == null) {
				fdOpen = new FileDialog(this, Strings.get("PNG 파일 열기"), FileDialog.LOAD);
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
					alert(Strings.get("해석할 수 없는 파일입니다."));
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
		return openPng(path, null);
	}
	private boolean openPng(String path, String key) {
		logger.info("openPng: " + path);
		
		try {
			BufferedImage bmp = null;
			
			File file = null;
			
			if (path.indexOf("http:") >= 0) {
				path = path.substring(path.indexOf("http:")).replace('\\', '/').replace(":/", "://").replace("///", "//");
				bmp = ImageIO.read(fileFromUrl(path));
				
			} else if (path.indexOf("https:") >= 0) {
				path = path.substring(path.indexOf("https:")).replace('\\', '/').replace(":/", "://").replace("///", "//");
				if (path.startsWith("https://pbs.twimg.com/media/")) {
					logger.info("트위터 이미지 URL");
					String[] params = null;
					int index = path.indexOf("?");
					if (index > 0) {
						params = path.substring(index + 1).split("&");
						path = path.substring(0, index);
					}
					if (path.indexOf(".", 28) < 0) {
						if (index < 0) {
							return false;
						}
						boolean isPng = false;
						for (String param : params) {
							if (param.startsWith("format=")) {
								if (param.substring(7).toLowerCase().equals("png")) {
									isPng = true;
									break;
								} else {
									logger.warn("PNG가 아님");
									return false;
								}
							}
						}
						if (isPng) {
							tfPngFile.setText(path = path + ".png:orig");
						} else {
							logger.warn("이미지 종류를 알 수 없음");
							return false;
						}
						
					} else {
						if (!path.substring(path.indexOf(".", 28) + 1).toLowerCase().startsWith("png")) {
							logger.warn("PNG가 아님");
							return false;
						}
					}
				}
				
				String contentType = "";
				try {
					contentType = getContentType(path);
				} catch (Exception e) {
					logger.debug(e);
					try {
						contentType = getContentType("http://" + path.substring(8));
					} catch (Exception e2) {
						logger.debug(e2);
					}
				}
				// 텍스트일 경우 소스코드 내 이미지 URL 찾기
				if (contentType.startsWith("text")) {
					List<String> imgUrls = new ArrayList<>();
					try {
						imgUrls = getImgUrls(textFromUrl(path));
					} catch (Exception e) {
						logger.debug(e);
						try {
							imgUrls = getImgUrls("http://" + path.substring(8));
						} catch (Exception e2) {
							logger.debug(e2);
						}
					}
					// 이미지 리스트 전체 돌리기 (비밀번호 입력 없이)
					for (String imgUrl : imgUrls) {
						logger.info("imgUrl: " + imgUrl);
						try {
							bmp = ImageIO.read(fileFromUrl(imgUrl));
						} catch (Exception e) {
							logger.debug(e);
							try {
								bmp = ImageIO.read(fileFromUrl("http://" + path.substring(8)));
							} catch (Exception e2) {
								logger.debug(e2);
								continue;
							}
						}
						if (bmp != null && openBitmap(bmp, file, key, false)) {
							return true;
						}
						bmp = null;
					}
				} else {
					try {
						bmp = ImageIO.read(fileFromUrl(path));
					} catch (Exception e) {
						logger.debug(e);
						try {
							bmp = ImageIO.read(fileFromUrl("http://" + path.substring(8)));
						} catch (Exception e2) {
							logger.debug(e2);
						}
					}
				}
				
			} else {
				file = new File(path);
				bmp = ImageIO.read(file);
			}
			
			if (bmp != null && openBitmap(bmp, file, key, true)) {
				return true;
			}
			
		} catch (Exception e) {
			logger.warn("PNG 파일 열기 실패");
			logger.debug(e);
		}
		return false;
	}
	private static List<String> getImgUrls(String html) {
		List<String> imgUrls = new ArrayList<>();
		int index = 0;
		try {
			while ((index = html.indexOf("<img ")) >= 0) {
				html = html.substring(index + 5);
				if ((index = html.indexOf("src=")) < 0) {
					break;
				}
				html = html.substring(index + 4);
				char q = html.charAt(0);
				if (q == '"' || q == '\'') {
					html = html.substring(1);
					int endIndex = html.indexOf(q);
					if (endIndex > 0) {
						imgUrls.add(html.substring(0, endIndex));
						html = html.substring(endIndex + 1);
					}
				} else {
					int endIndex = html.indexOf(">");
					if (endIndex > 0) {
						imgUrls.add(html.substring(0, endIndex).split(" ")[0]);
						html = html.substring(endIndex + 1);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return imgUrls;
	}
	private boolean openBitmap(BufferedImage bmp) {
		return openBitmap(bmp, null, null, true);
	}
	/**
	 * 비트맵 이미지 해석
	 * @param bmp
	 * @param file
	 * @param key
	 * @param retry 해석 실패 시 비밀번호 입력창 표시 여부
	 * @return 성공여부
	 */
	private boolean openBitmap(BufferedImage bmp, File file, String key, boolean retry) {
		try {
			int possibility = Container.WithTarget.possibility(bmp);
			Container.WithTarget parsed = null;
			if (key == null) {
				parsed = Container.WithTarget.fromBitmap(bmp, possibility);
			} else {
				// 파라미터 키값 가지고 시도
				parsed = Container.WithTarget.fromBitmap(bmp, possibility, key);
				if (parsed == null) {
					// 파라미터 키값 없이 재시도
					parsed = Container.WithTarget.fromBitmap(bmp, possibility);
				}
			}
			if (parsed == null) {
				if (!retry) {
					return false;
				}
				if (parsed == null) {
					// 키값 입력받아서 재시도
					key = JOptionPane.showInputDialog(this, Strings.get("이미지를 해석할 수 없습니다.\n비밀번호가 있다면 키를 입력하세요."));
					parsed = Container.WithTarget.fromBitmap(bmp, possibility, key);
				}
				if (parsed == null) {
					return false;
				}
			}
			
			// 파일에서 열었을 경우 열려있는 파일 설정
			if (file != null) {
				pngFile = file;
				tfPngFile.setText(pngFile.getAbsolutePath());
				explorer.setContainers(parsed.containers, file.getName(), false);
			} else {
				explorer.setContainers(parsed.containers, false);
			}
			openedImage = bmp;
			updateInfo();
			
			updateTarget(parsed.targetImage == null ? JUNK_IMAGE : parsed.targetImage);
			
			switch (parsed.type) {
				case Container.WithTarget.TYPE_429:
				case Container.WithTarget.TYPE_114v1:
				case Container.WithTarget.TYPE_114v2:
				case Container.WithTarget.TYPE_114v3: {
					rbTarget114.setSelected(true);
					tfRatioW.setEditable(false);
					tfRatioH.setEditable(false);
					break;
				}
				case Container.WithTarget.TYPE_238: {
					rbTarget238.setSelected(true);
					tfRatioW.setEditable(false);
					tfRatioH.setEditable(false);
					break;
				}
				case Container.WithTarget.TYPE_124: {
					rbTarget124.setSelected(true);
					tfRatioW.setEditable(false);
					tfRatioH.setEditable(false);
					break;
				}
				default: {
					rbTarget011.setSelected(true);
					tfRatioW.setEditable(true);
					tfRatioH.setEditable(true);
				}
			}
			tfPw.setText(key);
			ivOutput.setIcon(makeImageIcon(outputImage = openedImage));
			jlOutput.setText(Strings.get("출력 이미지") + "(" + outputImage.getWidth() + "×" + outputImage.getHeight() + ")");
			
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
			if (rbTarget114.isSelected() || rbTarget238.isSelected() || rbTarget124.isSelected()) {
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
	 * @param c: 붙여넣기 동작할 컴포넌트
	 * @return 성공여부
	 */
	private boolean pasteFromClipboard(Component c) {
		return pasteFromClipboard(Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null), c, false);
	}
	@SuppressWarnings("unchecked")
	private boolean pasteFromClipboard(Transferable tr, Component c, boolean isMouse) {
		logger.info("pasteTargetImage");
		
		if (tr.isDataFlavorSupported(DataFlavor.imageFlavor)) {
			logger.info("이미지일 경우 -> 출력물에 활용할 이미지로 붙여넣기");
			try {
				BufferedImage image = null;
				
				Object data = tr.getTransferData(DataFlavor.imageFlavor);
				if (data instanceof BufferedImage) {
					image = (BufferedImage) data;
				} else if (data instanceof AbstractMultiResolutionImage) {
					AbstractMultiResolutionImage mrci = (AbstractMultiResolutionImage) data;
					for (Image img : mrci.getResolutionVariants()) {
						if (img instanceof BufferedImage) {
							image = (BufferedImage) img;
							break;
						}
					}
				}
				if (image == null) {
					throw new Exception("BufferedImage from DataFlavor failed.");
				}
				int type = image.getType();
				logger.info("Image type: " + type);
				if (type != BufferedImage.TYPE_INT_RGB && type != BufferedImage.TYPE_3BYTE_BGR) {
					logger.info("Remake image");
					int w = image.getWidth();
					int h = image.getHeight();
					BufferedImage tmp = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
					for (int x = 0; x < w; x++) {
						for (int y = 0; y < h; y++) {
							tmp.setRGB(x, y, image.getRGB(x, y) & 0xFFFFFF);
						}
					}
					image = tmp;
					// TODO: 맥에선 이걸로 해결 안 되는 것 같음...
					// 일단 BufferedImage.TYPE_4BYTE_ABGR_PRE 인 건 확인
				}
				
				if (c == tfPngFile) {
					tfPngFile.setText("[Clipboard Image]");
					openBitmap(image);
				} else {
					updateTarget(image);
					if (rbTarget114.isSelected() || rbTarget238.isSelected() || rbTarget124.isSelected()) {
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
				dropFiles((List<File>) tr.getTransferData(DataFlavor.javaFileListFlavor), (JComponent) c);
			} catch (Exception e) {
				logger.warn("파일 붙여넣기 실패");
				logger.debug(e);
			}
		} else if (tr.isDataFlavorSupported(DataFlavor.stringFlavor)) {
			if (c == tfExportDir) {
				return false;
			}
			if (c == tfPngFile) {
				if (isMouse) { // Ctrl+V가 아닌 우클릭이었을 때
					logger.info("텍스트일 경우: 해당 경로 이미지 불러오기");
					try {
						String path = (String) tr.getTransferData(DataFlavor.stringFlavor);
						tfPngFile.setText(path);
						if (openPng(path)) {
							return true;
						} else {
							alert(Strings.get("해석할 수 없는 이미지 경로입니다."));
						}
						
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
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
			alert(Strings.get("이미지로 저장할 내용이 없습니다."));
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
					alert(Strings.get("저장되지 않았습니다."));
				}
			}
		} else {
			if (fdSave == null) {
				fdSave = new FileDialog(this, Strings.get("PNG 파일 저장"), FileDialog.SAVE);
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
					alert(Strings.get("저장되지 않았습니다."));
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
			ImageIO.write(openedImage = outputImage, "PNG", file);
			tfPngFile.setText((pngFile = file).getAbsolutePath());
			explorer.setRootName(file.getName(), true);
			saveConfig();
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
		copyToClipboard(outputImage);
	}
	private void copyToClipboard(Image image) {
		logger.info("copyToClipboard: " + image);
		if (image == null) {
			alert(Strings.get("이미지로 저장할 내용이 없습니다."));
		} else {
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
				
				alert(Strings.get("이미지가 클립보드에 복사됐습니다."));
				
			} catch (Exception e) {
				logger.error("이미지 복사 실패");
				logger.debug(e);
				alert(Strings.get("클립보드에 접근하지 못했습니다."));
			}
		}
	}
	
	/**
	 * 파일 추가
	 * @param files
	 */
	private void addFiles(File[] files) {
		logger.info("addFiles");
		List<FileItem> items = new ArrayList<>();
		List<String> paths = new ArrayList<>();
		String dir = explorer.getDir();
		for (FileItem item : explorer.getDirItems()) {
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
					cont.path = dir + cont.path;
					if (paths.contains(cont.path)) {
						if (confirm(Strings.get("파일 경로가 중복됩니다.\n덮어쓰시겠습니까?") + "\n" + cont.path, Strings.get("파일 중복"))) {
							// 기존 것 찾아서 삭제
							for (FileItem item : items) {
								if (cont.path.equals(item.container.path)) {
									explorer.remove(item, false);
								}
							}
						} else {
							// 추가 안 하고 넘김
							continue;
						}
					}
					explorer.add(new FileItem(cont, file.getParent() + "/" + cont.path), false);
					count++;
				}
			} catch (Exception e) {
				logger.error("파일 추가 실패");
				logger.debug(e);
			}
		}
		if (count > 0) {
			explorer.sort();
			updateOutput();
		}
	}
	
	/**
	 * 선택된 파일 삭제
	 */
	public void deleteSelected() {
		logger.info("deleteSelected");
		explorer.removeSelected(true);
	}
	
	/**
	 * 선택된 파일 추출
	 */
	private void exportSelected() {
		String exportPath = tfExportDir.getText();
		if (exportPath.replace('\\', '/').startsWith(System.getProperty("java.io.tmpdir").replace('\\', '/'))) {
			alert(Strings.get("임시 파일 경로에는 추출할 수 없습니다."));
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
		List<Container> containers = explorer.getSelectedContainers();
		if (containers.size() == 0) {
			showMessage(Strings.get("선택된 파일이 없습니다."), useAlert);
			return;
		}
		
		List<File> files = Container.containersToFiles(containers, exportPath);
		if (files.size() > 0) {
			showMessage(Strings.get("파일을 추출했습니다."), useAlert);
		} else {
			showMessage(Strings.get("파일을 추출하지 못했습니다."), useAlert);
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
			if (isAndroid) {
				if (!openPng(tfPngFile.getText())) {
					alert(Strings.get("해석할 수 없는 이미지 경로입니다."));
				}
			} else {
				openPng();
			}
			
		} else if (target == btnClose) {
			// PNG 파일 닫기
			explorer.clear();
			pngFile = null;
			tfPngFile.setText("");
			openedImage = null;
			updateTarget(JUNK_IMAGE);
			updateOutput();
			
		} else if (target == btnExport) {
			exportSelected();
			
		} else if (target == btnSave) {
			// PNG 파일 저장
			checkAndSave();
			
		} else if (target == btnCopy) {
			// 이미지 클립보드 복사
			copyToClipboard();
			
		} else if (target == rbTarget114
				|| target == rbTarget238
				|| target == rbTarget124
				|| target == rbTarget011
				) {
			if (target == rbTarget011) {
				// 비율 입력 활성화
				tfRatioW.setEditable(true);
				tfRatioH.setEditable(true);
				
			} else {
				// 비율 입력 비활성화
				tfRatioW.setEditable(false);
				tfRatioH.setEditable(false);
				tfRatioW.setText(""+targetImage.getWidth());
				tfRatioH.setText(""+targetImage.getHeight());
			}
			// 이미지 사용 여부 선택하면 재생성
			updateOutput();
		}
	}
	private void addFileWithDialog() {
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
					if (e.getComponent() == tfPw) {
						break;
					}
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
					pasteFromClipboard(e.getComponent());
					e.consume();
				}
				break;
			}
			case 10: { // Enter
				Component c = e.getComponent();
				if (c == tfPngFile) {
					// 이미지 경로로 파일 열기
					if (!openPng(tfPngFile.getText())) {
						alert(Strings.get("해석할 수 없는 이미지 경로입니다."));
					}
				} else if (c == tfWidth || c == tfRatioW || c == tfRatioH || c == tfPw) {
					try {
						// 최소 크기 변경 없어도 엔터면 이미지 재생성
						updateOutput();
					} catch (Exception ex) {
						logger.warn("올바른 크기를 입력하세요.");
						logger.debug(ex);
						alert(Strings.get("올바른 크기를 입력하세요."));
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
	public void runFile(Container cont) {
		List<Container> containers = new ArrayList<>();
		containers.add(cont);
		
		// 임시 파일 생성
		String rootDir = TMP_DIR + Calendar.getInstance().getTimeInMillis();
		List<File> files = Container.containersToFiles(containers, rootDir);
		
		if (files.size() > 0) {
			String path = files.get(0).getAbsolutePath();
			
			// 파일 유형 확인
			String type = null;
			int index = path.lastIndexOf('.');
			if (index > 0) {
				type = path.substring(index + 1).toLowerCase();
			}
			if ("bmp".equals(type)
			 || "png".equals(type)
			 || "gif".equals(type)
			 || "jpg".equals(type)
			 || "jpeg".equals(type)
			) {
				type = "image?";
			}
			
			try {
				// OS와 파일 유형에 따른 실행 명령어 설정
				logger.debug("os: " + OS);
				String cmd = null;
				if (isWindows) {
					if ("image?".equals(type)) {
						cmd = "mspaint";
					} else {
						cmd = "notepad";
					}
				} else if (isLinux) {
					if ("image?".equals(type)) {
						cmd = "eog";
					} else {
						cmd = "cat";
					}
				} else if (isMac) {
					cmd = "open";
				}
				if (cmd != null) {
					Runtime.getRuntime().exec(new String[] { cmd, path });
				}
			} catch (Exception e) {
				logger.error("실행 실패: " + path);
				logger.debug(e);
			}
		}
	}
	@Override
	public void requestCheckError() {
		if (openedImage != null) {
			String key = JOptionPane.showInputDialog(GUI.this, Strings.get("비밀번호 걸린 이미지가 잘못 해석된 것 같다면\n비밀번호 키를 입력하세요."));
			openBitmap(openedImage, pngFile, key, false);
		}
	}
	@Override
	public void onSelectionChanged() {
		List<Container> conts = explorer.getSelectedContainers();
		if (conts.size() == 0) {
			labelStatus.setText("");
		} else {
			int size = 0;
			for (Container cont : conts) {
				size += cont.binary.length;
			}
			labelStatus.setText(Strings.get("{count} 파일 선택됨 / {size}").replace("{count}", ""+conts.size()).replace("{size}", strSize(size)));
		}
	}
	@Override
	public void onUpdate() {
		updateOutput();
	}
	
	@Override
	public void onPopup(MouseEvent e, String dir, List<FileItem> items) {
		JPopupMenu menu = new JPopupMenu();
		
		if (items.size() > 0) {
			if (items.size() == 1) {
				menu.add(miOpenFile);
				menu.add(miRename);
			}
			menu.add(miRemove);
			menu.add(miCopyFiles);
			menu.add(new JPopupMenu.Separator());
		}
		menu.add(miAddFile);
		if (!explorer.isEmpty()) {
			menu.add(miSelectAll);
		}
		if (openedImage != null && explorer.getAllContainers().size() == 1) {
			menu.add(new JPopupMenu.Separator());
			menu.add(miIfError);
		}
		
		menu.show(explorer, e.getX() + explorer.getDirWidth(), e.getY());
	}

	@Override
	public void keyReleased(KeyEvent e) {
		Component component = e.getComponent();
		if (component == tfWidth || component == tfRatioW || component == tfRatioH) {
			if (e.getKeyCode() == 10) {
				// 엔터일 경우 KeyPressed에서 이미 동작함
				return;
			}
			JTextField tf = (JTextField) component;
			String strValue = tf.getText().trim();
			try {
				Integer.parseInt(strValue);
				// 최소 크기 변경 이미지 재생성
				if (tf == tfWidth) {
					updateOutput();
				}
			} catch (Exception ex) {
				logger.warn("올바른 숫자를 입력하세요.");
				logger.debug(ex);
				alert(Strings.get("최소 크기가 너무 큽니다.\n1920 이하의 값을 적어주세요."));
				
				char[] cs = strValue.toCharArray();
				strValue = "";
				for (char c : cs) {
					if (c >= '0' && c <= '9') {
						strValue += c;
					}
				}
				if (strValue.length() == 0) {
					strValue = (tf == tfWidth) ? "0" : "1";
				}
				tf.setText(strValue);
			}
			return;
			
		} else if (component == tfPw) {
			updateOutput();
			return;
		}
		switch (e.getKeyCode()) {
			case 27: { // ESC
				explorer.clearSelection();
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
			
        	if (c == ivOutput || c == ivTarget) { // 이미지뷰 드래그
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
        		if (image == null) {
    				logger.info("드래그할 이미지 없음");
        			return null;
        		}
        		
        		// 임시 PNG 파일 생성해서 전달
        		File file = new File(TMP_DIR + name);
        		file.deleteOnExit();
        		
				// 파일만 생성해서 전달 후 스레드에서 PNG 파일 내용 작성
				final BufferedImage fileImage = image; // 스레드 전달용 final
				new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							ImageIO.write(fileImage, "PNG", file);
						} catch (IOException e) {
		    				logger.error("임시 파일 쓰기 실패");
							logger.debug(e);
						}
					}
				}).start();
				rootFiles.add(file);
        	}
        	
        	if (rootFiles.size() > 0) {
        		return new FileTransferable(rootFiles);
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
			if (e.getButton() == MouseEvent.BUTTON1) {
				fth.exportAsDrag(c, e, TransferHandler.MOVE);
			}
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
    	
    	@Override
    	public synchronized void dragEnter(DropTargetDragEvent evt) {
    		logger.debug("FileDropTarget.dragEnter");
    		
    		// 드래그 들어왔을 때 테두리 표현
			normalBorder = c.getBorder();
			c.setBorder(DRAG_BORDER);
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
				
				if (c == panelTarget || c == ivTarget) {
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
					
					if (!confirm(Strings.get("이미지를 적용하시겠습니까?"), Strings.get("이미지 파일 열기"))) {
						return;
					}
					if (!setTargetImage(file.getAbsolutePath())) {
						alert(Strings.get("이미지를 적용할 수 없습니다."));
					}
					
				} else {
					// PNG 파일 열기
					if (!ext.equals("png")) {
						if (c == panelPngFile || c == tfPngFile) {
							alert(Strings.get("해석할 수 없는 파일입니다."));
						}
						break;
					}
					
					if (!explorer.isEmpty()) {
						if (file.getAbsolutePath().replace('\\', '/').startsWith(TMP_DIR)) {
							// 임시 파일이면 잘못 드래그한 경우
							logger.info("임시 파일 무시");
							return;
						}
						if (!confirm(Strings.get("현재 파일을 닫겠습니까?"), Strings.get("PNG 파일 열기"))) {
							if (confirm(Strings.get("파일 목록에 추가하시겠습니까?"), Strings.get("PNG 파일 열기"))) {
								c = panelFilesEdit;
								break;
							}
							return;
						}
					}
					if (!openPng(file.getAbsolutePath())) {
						alert(Strings.get("해석할 수 없는 PNG 파일입니다.\n파일 목록에 추가합니다."));
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
		logger.debug("Content-Length: " + contentLength);
		if (contentLength != null) {
			int size = 0;
			try {
				size = Integer.parseInt(contentLength);
			} catch (Exception e) {
				logger.warn("크기를 알 수 없음");
			}
			if (size > 20971520) {
				throw new Exception("20MB를 초과합니다");
			}
		}
		
		String contentDisposition = conn.getHeaderField("Content-Disposition");
		logger.debug("Content-Disposition: " + contentDisposition);
		String filename = null;
		if (contentDisposition == null) {
			String path = url.getPath();
			filename = path.substring(path.lastIndexOf("/") + 1);
			if (filename.indexOf('?') > 0) {
				filename = filename.substring(0, filename.lastIndexOf('?'));
			}
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
	private static String textFromUrl(String strUrl) throws Exception {
		logger.info("textFromUrl: " + strUrl);
		URL url = new URL(strUrl);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setConnectTimeout(2000);
		conn.setReadTimeout(2000);
		conn.setRequestMethod("GET");
		conn.getResponseCode();

		BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
		String line;
		StringBuffer response = new StringBuffer();

		while ((line = in.readLine()) != null) {
			response.append(line);
		}
		in.close();
		
		return response.toString();
	}
	private static String getContentType(String strUrl) throws Exception {
		logger.info("getContentType: " + strUrl);
		URL url = new URL(strUrl);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setConnectTimeout(2000);
		conn.setReadTimeout(2000);
		conn.setRequestMethod("GET");
		conn.getResponseCode();
		String contentType = conn.getHeaderField("Content-Type");
		logger.info("contentType: " + contentType);
		return contentType;
	}
	
	public static void main(String[] args) {
		Container.setLogger(logger);
		
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		GUI gui = new GUI();
		
		// 3번 인자: 비밀번호, 있으면 바로 종료
		boolean disposeAfterExport = (args.length > 2);
		if (!disposeAfterExport) {
			gui.init();
		}
		
		// 1번 인자: PNG 파일 경로
		if (args.length > 0) {
			// PNG 파일 열기
			if (!gui.openPng(args[0], args.length > 2 ? args[2] : null)) {
				gui.alert("이미지를 해석하지 못했습니다.");
				if (disposeAfterExport) {
					gui.dispose();
				}
				return;
			}
			
			// 2번 인자: 추출 경로
			if (args.length > 1) {
				// 전체 선택
				gui.explorer.selectAll();
				
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