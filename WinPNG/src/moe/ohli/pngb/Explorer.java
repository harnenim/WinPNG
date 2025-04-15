package moe.ohli.pngb;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/**
 * 좌측 트리가 있는 탐색기
 * 
 * @author harne_
 *
 */
@SuppressWarnings("serial")
public class Explorer extends JPanel {
	private static final String TMP_DIR = (System.getProperty("java.io.tmpdir").replace('\\', '/') + "/WinPNG/").replace("//", "/");
	private static final Color SELECTED_COLOR = new Color(204, 232, 255);
	private static final Border FOCUS_BORDER = BorderFactory.createMatteBorder(1, 1, 1, 1, new Color(153, 209, 255));
	
	// 구버전에서 잘못 생성된 아이콘 임시파일 삭제
	static {
		try { // 극초기 버전에서 상위폴더에 시간.확장자 형식으로 파일 증식했던 것
			File[] files = new File(System.getProperty("java.io.tmpdir")).listFiles();
			for (File file : files) {
				if (file.isDirectory()) {
					continue;
				}
				String name = file.getName();
				if (!name.startsWith("icon")) {
					continue;
				}
				try {
					Integer.parseInt(name.substring(4, 10));
					file.delete();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		try { // 파일명 중간에 .이 있는 경우
			File[] files = new File(TMP_DIR + "icon").listFiles();
			for (File file : files) {
				if (file.isDirectory()) {
					continue;
				}
				String name = file.getName();
				if (name.indexOf(".") == name.lastIndexOf(".")) {
					continue;
				}
				try {
					file.delete();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 파일 드래그 객체
	 * 
	 * @author harne_
	 *
	 */
    public static class FileTransferable implements Transferable {
		private List<File> files;
		
		public FileTransferable(List<File> files) {
			this.files = files;
		}
		
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
    		return files;
		}
    }
    
    /**
     * 탐색기 파일 객체
     * 
     * @author harne_
     *
     */
	public static class FileItem {
		public Container container = null;
		public String originalPath = null;
		public String label = null;
		public FileItem(Container container) {
			this.container = container;
			container.path = container.path.replace('\\', '/');
			refreshLabel();
		}
		public FileItem(Container container, String originalPath) {
			this.container = container;
			this.originalPath = originalPath;
			container.path = container.path.replace('\\', '/');
			refreshLabel();
		}
		public FileItem(String label) {
			this.label = label;
		}
		public void clearOriginalPath() {
			if (originalPath != null) {
				originalPath = null;
				refreshLabel();
			}
		}
		public void refreshLabel() {
			if (container == null) return; // 표현용 디렉토리는 건드릴 일 없음
			
			label = container.path.substring(container.path.lastIndexOf('/') + 1)
					+ (container.binary == null ? ""/* 강제 생성 디렉토리 */ :
						(" (" + comma(container.binary.length) + ")" + (originalPath == null ? "" : ": " + originalPath)));
		}
		public String getName() {
			if (container == null) {
				return label;
			}
			return container.path.substring(container.path.lastIndexOf('/') + 1);
		}
	}
	/**
	 * 리스너도 필요해짐
	 * 
	 * @author harne_
	 *
	 */
	public static interface Listener {
		public void runFile(Container cont);
		public void requestCheckError();
		public void onSelectionChanged();
		public void onUpdate();
		public void onPopup(MouseEvent evt, String dir, List<FileItem> items);
	}
	
	private static class DirTreeNode extends DefaultMutableTreeNode {
		public DirTreeNode(Object arg0) {
			super(arg0);
		}
		public DirTreeNode findNode(String name) {
			@SuppressWarnings("unchecked")
			Enumeration<DefaultMutableTreeNode> children = children();
			while (children.hasMoreElements()) {
				DirTreeNode child = (DirTreeNode) children.nextElement();
				if (name.equals(child.getUserObject())) {
					return child;
				}
			}
			return null;
		}
	}
	
	/**
	 * 숫자 3자리 쉼표
	 * @param value
	 * @return
	 */
	private static String comma(int value) {
		String result = "" + (value % 1000);
		value /= 1000;
		while (value > 0) {
			while (result.length() % 4 < 3) {
				result = '0' + result;
			}
			result = (value % 1000) + "," + result;
			value /= 1000;
		}
		return result;
	}
	
	/**
	 * 시스템 파일 아이콘 구해오기
	 * @param name
	 * @return
	 * @throws IOException
	 */
	public static Icon getSystemIcon(String name) throws IOException {
		String path = TMP_DIR;
		
		if (name != null) { // null: 폴더 / not null: 파일
			String ext = name;
			if (ext.indexOf(".") > 0) {
				ext = ext.substring(ext.lastIndexOf("."));
			}
			path += "icon/icon" + ext;
		}

		File file = new File(path);
		if (!file.exists()) {
			new File(TMP_DIR + "icon").mkdirs();
			
			FileOutputStream fos = new FileOutputStream(file);
			// fos.write 할 거 없음
			
			if (fos != null) try { fos.close(); } catch (Exception e) { }
		}
		if (file.exists()) {
			return FileSystemView.getFileSystemView().getSystemIcon(file);
		}
		return null;
	}
	
	private List<FileItem> list = new ArrayList<>();
	private Logger logger;
	private Listener listener;
	private Icon rootIcon, dirIcon, fileIcon;
	
	private DirTreeNode dlRoot = new DirTreeNode("/");
	private JTree dlv = new JTree(dlRoot);
	private JScrollPane spd = new JScrollPane(dlv);
	private JPanel panelDir = new JPanel(new BorderLayout());
	private JPanel bar = new JPanel();

	private DefaultListModel<FileItem> flModel = new DefaultListModel<>();
	private JList<FileItem> flv = new RubberBandList<>(flModel);
	private JScrollPane spf = new JScrollPane(flv);
	
	private String currentDir = "";
	public String getDir() {
		return currentDir;
	}
	int fromWidth = 0;
	
	/**
	 * 탐색기 생성자
	 * @param logger
	 * @param listener
	 */
	public Explorer(Logger logger, Listener listener) {
		this(logger, listener, null);
	}
	public Explorer(Logger logger, Listener listener, String root) {
		super(new BorderLayout());
		this.logger = logger;
		this.listener = listener;
		
		{	// UI 배치
			panelDir.add(spd, BorderLayout.CENTER);
			panelDir.add(bar, BorderLayout.EAST);
			
			add(panelDir, BorderLayout.WEST);
			add(spf, BorderLayout.CENTER);
			
			setDirWidth(120);
			bar.setPreferredSize(new Dimension(7, 0));
			bar.setBackground(new Color(221, 221, 221));
			bar.setCursor(new Cursor(Cursor.E_RESIZE_CURSOR));
			bar.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 1, new Color(255, 255, 255)));
			spd.setBorder(null);
			spf.setBorder(null);

			dlv.setRowHeight(19);
			flv.setFixedCellHeight(19);
			flv.setFocusable(true);
		}
		
		{	// 디렉토리/파일 리스트 렌더러 설정
			DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer() {
				@Override
				public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
					Component c = null;
					if (value == dlRoot) { // 루트는 아이콘 예외처리
						openIcon = closedIcon = rootIcon;
						c = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
						openIcon = closedIcon = dirIcon;
					} else {
						c = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
					}
					return c;
				}
			};
			renderer.setBackgroundSelectionColor(SELECTED_COLOR);
			renderer.setBorderSelectionColor(SELECTED_COLOR);
			renderer.setTextSelectionColor(Color.BLACK);
			dlv.setCellRenderer(renderer);
			
			{	// 폴더/파일 아이콘
				dirIcon = renderer.getClosedIcon();
				fileIcon = renderer.getLeafIcon();
				renderer.setLeafIcon(dirIcon);
				try {
					dirIcon = getSystemIcon(null);
					renderer.setClosedIcon(dirIcon);
					renderer.setOpenIcon(dirIcon);
				} catch (Exception e) {
					logger.debug(e);
				}
				
				// 루트 아이콘
				rootIcon = dirIcon;
				if (root != null) {
					try {
						rootIcon = getSystemIcon(root);
					} catch (Exception e) {
						logger.debug(e);
					}
				}
			}
			
			flv.setCellRenderer(new DefaultListCellRenderer() {
				@Override
				public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean hasFocus) {
					FileItem item = (FileItem) value;
					Icon icon = dirIcon;
					if (item.container != null && item.container.binary != null) {
						icon = fileIcon;
						try {
							Icon systemIcon = getSystemIcon(item.getName());
							if (systemIcon != null) {
								icon = systemIcon;
							}
						} catch (IOException e) {
							logger.debug(e);
						}
					}
					JLabel label = new JLabel(item.label);
					label.setOpaque(true);
					label.setIcon(icon);
					label.setBackground(selected ? SELECTED_COLOR : Color.WHITE);
				    label.setBorder(hasFocus ? FOCUS_BORDER : noFocusBorder);
					return label;
				}
			});
		}
		
		{	// 이벤트 리스너
			dlv.addTreeSelectionListener(new TreeSelectionListener() {
				@Override
				public void valueChanged(TreeSelectionEvent e) {
					TreePath tp = dlv.getSelectionPath();
					if (tp == null) {
						return;
					}
					String path = "/";
					Object[] dirs = tp.getPath();
					for (int i = 1; i < dirs.length; i++) {
						path += (path.length() > 1 ? "/" : "") + dirs[i];
					}
					openDir(path, true);
				}
			});
			
			flv.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent evt) {
					int button = evt.getButton();
					logger.info("mouseClicked: " + button);
					if (evt.getClickCount() == 1) {
						if (button == MouseEvent.BUTTON3 || button == 0) { // Android JRE의 RMB가 0이 나옴
							// 우클릭 메뉴
							listener.onPopup(evt, currentDir, flv.getSelectedValuesList());
						} else if (button == 4) {
							// 뒤로가기 대신 상위폴더로 작동
							cd("..");
						}
						
					} else if (evt.getClickCount() == 2) {
						if (button == MouseEvent.BUTTON1) {
							logger.debug("더블 클릭");
							openSelectedFile(); // 실행
						}
					}
				}
			});
			flv.addKeyListener(new KeyAdapter() {
				@Override
				public void keyReleased(KeyEvent e) {
					switch (e.getKeyCode()) {
						case 27: { // ESC
							// 선택 취소
							clearSelection();
							break;
						}
						case 127: { // Delete
							// 선택 삭제
							removeSelected(true);
							break;
						}
						case 113: { // F2
							renameSelected();
						}
					}
				}
				@Override
				public void keyPressed(KeyEvent e) {
					switch (e.getKeyCode()) {
						case 10: { // Enter
							// 실행
							openSelectedFile();
						}
						case 37: { // ←
							if (e.isAltDown()) { // 뒤로가기 대신 상위폴더로 작동
								cd("..");
							}
						}
					}
				}
			});
			flv.setTransferHandler(new TransferHandler() {
				@Override
				protected Transferable createTransferable(JComponent c) {
					return Explorer.this.createTransferable();
				}
				
				@Override
				public int getSourceActions(JComponent c) {
					return MOVE;
				}
			});
			flv.addListSelectionListener(new ListSelectionListener() {
				@Override
				public void valueChanged(ListSelectionEvent e) {
					listener.onSelectionChanged();
				}
			});
			
			{	// 디렉토리 트리 사이즈 조절
				MouseAdapter maResize = new MouseAdapter() {
					private int fromX = 0;
					
					@Override
					public void mousePressed(MouseEvent e) {
						fromX = (int) e.getPoint().getX();
						fromWidth = getDirWidth();
					}
					@Override
					public void mouseDragged(MouseEvent e) {
						if (fromWidth == 0) return;
						int width = fromWidth + (int) e.getPoint().getX() - fromX;
						setDirWidth(width < 1 ? 1 : width);
					}
					@Override
					public void mouseReleased(MouseEvent e) {
						if (fromWidth == 0) return;
						fromWidth = 0;
					}
				};
				addMouseListener(maResize);
				addMouseMotionListener(maResize);
				addKeyListener(new KeyAdapter() {
					@Override
					public void keyReleased(KeyEvent e) {
						switch (e.getKeyCode()) {
							case 27: { // ESC
								// 리사이즈 취소
								if (fromWidth == 0) return;
								setDirWidth(fromWidth);
								fromWidth = 0;
								break;
							}
						}
					}
				});
			}
		}
	}
	@Override
	public synchronized void addKeyListener(KeyListener kl) {
		flv.addKeyListener(kl);
	}
	public Transferable createTransferable() {
		logger.debug("createTransferable");
		final List<File> rootFiles = new ArrayList<>();
		
		List<Container> containers = getSelectedContainers();
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
		
		if (rootFiles.size() > 0) {
			return new FileTransferable(rootFiles);
		}
		return null;
	}
	
	public void setDirWidth(int width) {
		panelDir.setPreferredSize(new Dimension(width, 0));
		updateUI();
	}
	public int getDirWidth() {
		return panelDir.getWidth();
	}
	
	public boolean isEmpty() {
		for (FileItem item : list) {
			if (item.container.binary != null) {
				return false;
			}
		}
		return true;
	}
	public void clear() {
		logger.info("Explorer.clear");
		list.clear();
		currentDir = "";
		dlRoot.setUserObject("/");
		refresh(true);
	}
	public void add(FileItem item) {
		add(item, true);
	}
	public void add(FileItem item, boolean withUpdate) {
		logger.info("Explorer.add: " + item.container.path);
		item.container.path = item.container.path.replace('\\', '/');
		list.add(item);
		refresh(withUpdate);
	}
	public void addWithoutRefresh(FileItem item) {
		logger.info("Explorer.addWithoutRefresh: " + item.container.path);
		item.container.path = item.container.path.replace('\\', '/');
		list.add(item);
	}
	public void addFolder() {
		String name = JOptionPane.showInputDialog(this, Strings.get("폴더명을 입력하세요."));
		if (!isValidFileName(name)) {
			if (name != null) {
				JOptionPane.showMessageDialog(this, Strings.get("올바른 이름이 아닙니다."));
			}
			return;
		}
		logger.info("Explorer.addFolder: " + name);
		list.add(new FileItem(new Container(getDir() + name)));
		refresh(false); // 폴더 생성은 결과물에 영향 없음
	}
	public void remove(FileItem item) {
		remove(item, true);
	}
	public boolean remove(FileItem item, boolean withUpdate) {
		logger.info("Explorer.remove: " + item.container.path);
		if (list.remove(item)) {
			refresh(withUpdate);
			return true;
		}
		return false;
	}
	public boolean removeWithoutRefresh(FileItem item) {
		logger.info("Explorer.removeWithoutRefresh: " + item.container.path);
		if (list.remove(item)) {
			return true;
		}
		return false;
	}
	public void removeSelected() {
		removeSelected(true);
	}
	public void removeSelected(boolean withUpdate) {
		logger.info("Explorer.removeSelected");
		List<FileItem> removeList = new ArrayList<>();
		for (FileItem item : flv.getSelectedValuesList()) {
			if (item.container == null || item.container.binary == null) {
				String path = currentDir + item.label;
				String subDir = path + "/";
				for (FileItem subItem : list) {
					if (subItem.container.path.startsWith(subDir)) {
						removeList.add(subItem);
					} else if (path.equals(subItem.container.path)) {
						removeList.add(subItem);
					}
				}
			}
			removeList.add(item);
		}
		for (FileItem item : removeList) {
			list.remove(item);
		}
		refresh(withUpdate);
	}
	private static final char[] antiChars = "\\/:*?\"<>|".toCharArray(); // 파일명 비허용 문자
	public static boolean isValidFileName(String name) {
		if (name == null || name.length() == 0) {
			return false;
		}
		boolean notOnlyDots = false; // 점만 있는 파일명도 안 됨
		for (char c : name.toCharArray()) {
			for (char anti : antiChars) {
				if (c == anti) {
					return false;
				}
				if (c != '.') {
					notOnlyDots = true;
				}
			}
		}
		return notOnlyDots;
	}
	public void renameSelected() {
		logger.info("Explorer.renameSelected");
		FileItem selected = flv.getSelectedValue();
		if (selected == null) return;
		
		String oldName = selected.label; // 디렉토리
		if (selected.container != null) { // 파일
			oldName = selected.container.path.substring(selected.container.path.lastIndexOf('/') + 1);
		}
		String name = JOptionPane.showInputDialog(this, Strings.get("새 이름을 입력하세요."), oldName);
		logger.info("name: " + name);
		
		if (name == oldName) {
			// 수정사항 없음
			return;
		}
		
		if (!isValidFileName(name)) {
			if (name != null) {
				JOptionPane.showMessageDialog(this, Strings.get("올바른 이름이 아닙니다."));
			}
			return;
		}
		
		if (selected.container == null || selected.container.binary == null) {
			// 디렉토리
			String from = currentDir + selected.label;
			String to = currentDir + name;
			
			boolean canRename = true;
			for (FileItem item : list) {
				if (item.container.path.startsWith(to + "/")) {
					canRename = JOptionPane.showConfirmDialog(this, "이미 존재하는 폴더입니다. 합치시겠습니까?", "폴더명 중복", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
					break;
				}
			}
			if (canRename) {
				FileItem self = null;
				for (FileItem item : list) {
					if (item.container.path.equals(from)) {
						if (item.container.binary == null) {
							self = item;
						}
					} else if (item.container.path.startsWith(from + "/")) {
						item.container.path = to + item.container.path.substring(from.length());
					}
				}
				list.remove(self);
				refresh(true, from, to);
			}
			
		} else {
			// 파일
			String newPath = selected.container.path.substring(0, selected.container.path.lastIndexOf('/') + 1) + name;
			
			for (FileItem item : list) {
				if (newPath.equals(item.container.path)) {
					if (item.container.binary == null) {
						continue;
					}
					JOptionPane.showMessageDialog(this, Strings.get("이미 존재하는 파일명입니다."));
					return;
				}
			}
			
			selected.container.path = newPath;
			selected.refreshLabel();
			sort();
			cd(".");
			listener.onUpdate();
		}
	}
	public void selectAll() {
		logger.info("Explorer.selectAll");
		if (flModel.isEmpty()) {
			return;
		}
		int[] indices = new int[flModel.size()];
		for (int i = 0; i < indices.length; i++) {
			indices[i] = i;
		}
		flv.setSelectedIndices(indices);
	}
	public void clearSelection() {
		logger.info("Explorer.clearSelection");
		flv.clearSelection();
	}
	public List<Container> getAllContainers() {
		logger.info("Explorer.getAllContainers");
		List<Container> result = new ArrayList<>();
		for (FileItem item : list) {
			if (item.container.binary != null) {
				result.add(item.container);
			}
		}
		return result;
	}
	public List<FileItem> getDirItems() {
		logger.info("Explorer.getDirItems");
		List<FileItem> result = new ArrayList<>();
		for (FileItem subItem : list) {
			if (currentDir.length() == 0 || subItem.container.path.startsWith(currentDir)) {
				result.add(subItem);
			}
		}
		return result;
	}
	public List<Container> getSelectedContainers() {
		logger.info("Explorer.getSelectedContainers");
		List<Container> result = new ArrayList<>();
		for (FileItem item : flv.getSelectedValuesList()) {
			if (item.container == null || item.container.binary == null) {
				// 디렉토리일 경우 하위 모든 파일 선택
				String subDir = currentDir + item.label + "/";
				for (FileItem subItem : list) {
					if (subItem.container.binary == null) { // 폴더
						continue;
					}
					if (subItem.container.path.startsWith(subDir)) {
						// 앞에 경로 뗀 컨테이너 생성
						result.add(subItem.container.copy(subItem.container.path.substring(currentDir.length())));
					}
				}
			} else {
				// 조건문 없더라도, 선택된 건 애초에 현재 폴더 안에 있는 파일이어야 함
				if (currentDir.length() == 0 || item.container.path.startsWith(currentDir)) {
					// 앞쪽 경로 뗀 컨테이너 생성
					result.add(item.container.copy(item.container.path.substring(currentDir.length())));
				}
			}
		}
		return result;
	}
	
	public void setRootName(String name) {
		setRootName(name, false);
	}
	public void setRootName(String name, boolean withClearOriginalPaths) {
		logger.info("Explorer.setRootName: " + name);
		dlRoot.setUserObject(name);
		if (withClearOriginalPaths) {
			clearOriginalPaths();
		} else {
			refresh(false);
		}
	}
	public void setContainers(List<Container> containers, boolean withUpdate) {
		setContainers(containers, "/", withUpdate);
	}
	public void setContainers(List<Container> containers, String name, boolean withUpdate) {
		logger.info("Explorer.setContainers " + name + (withUpdate ? " with update" : ""));
		// 전체 초기화일 경우 트리 모두 접기
		for (int i = dlv.getRowCount() - 1; i > 0; i--) {
			dlv.collapseRow(i);
		}
		
		List<String> dirs = new ArrayList<>();
		
		list.clear();
		for (Container cont : containers) {
			cont.path = cont.path.replace('\\', '/');
			
			// 폴더 객체도 생성해둠 (파일 삭제 시에도 폴더 계속 보이도록)
			int index = 0;
			while ((index = cont.path.indexOf("/", index + 1)) > 0) {
				String dir = cont.path.substring(0, index);
				if (!dirs.contains(dir)) {
					dirs.add(dir);
					list.add(new FileItem(new Container(dir)));
				}
			}
			
			list.add(new FileItem(cont));
		}
		dlRoot.setUserObject(name);
		refresh(withUpdate);
	}
	public void clearOriginalPaths() {
		logger.info("Explorer.clearOriginalPaths");
		for (FileItem item : list) {
			item.clearOriginalPath();
		}
		refresh(false);
	}
	public void refresh(boolean withUpdate) {
		refresh(withUpdate, "?", null);
	}
	public void refresh(boolean withUpdate, String replacedFrom, String replacedTo) {
		logger.info("Explorer.refresh" + (withUpdate ? " with update" : ""));
		sort();
		refreshTree(replacedFrom, replacedTo);
		cd("/" + currentDir);
		
		if (withUpdate) {
			listener.onUpdate();
		}
	}
	public void sort() {
		logger.info("Explorer.sort");
		Collections.sort(list, COMP);
	}
	private Map<String, TreePath> pathMap = new HashMap<>();
	public void refreshTree() {
		refreshTree("?", null);
	}
	public void refreshTree(String replacedFrom, String replacedTo) {
		logger.info("Explorer.refreshTree");
		logger.info(replacedFrom + " -> " + replacedTo);
		replacedFrom = dlRoot.getUserObject().toString() + "/" + replacedFrom;
		replacedTo   = dlRoot.getUserObject().toString() + "/" + replacedTo  ;
		
		// 열려있었던 것들 기억
		List<String> wasExpanded = new ArrayList<>();
		for (int i = 0; i < dlv.getRowCount(); i++) {
			String path = strPath(dlv.getPathForRow(i));
			if (dlv.isExpanded(i)) {
				if (path.startsWith(replacedFrom)) {
					path = replacedTo + path.substring(replacedFrom.length());
				}
				wasExpanded.add(path);
			}
			dlv.expandRow(i);
		}
		
		dlRoot.removeAllChildren();
		pathMap.clear();
		List<String> paths = new ArrayList<>();
		paths.add("/");
		
		for (FileItem item : list) {
			DirTreeNode parent = dlRoot;
			String[] names = item.container.path.split("/");
			String path = "";
			for (int i = 0; i < names.length - (item.container.binary == null ? 0 : 1); i++) {
				String name = names[i];
				
				path += name + "/";
				DirTreeNode node = parent.findNode(name);
				if (node != null) {
					parent = node;
					continue;
				}
				
				parent.add(parent = new DirTreeNode(name));
				paths.add(path);
			}
		}
		((DefaultTreeModel) dlv.getModel()).reload();
		
		// 전부 열어서 경로 기억
		for (int i = 0; i < dlv.getRowCount(); i++) {
			dlv.expandRow(i);
			pathMap.put(paths.get(i), dlv.getPathForRow(i));
		}
		// 원래 열려있었던 게 아니면 닫기
		for (int i = dlv.getRowCount() - 1; i > 0; i--) {
			String path = strPath(dlv.getPathForRow(i));
			if (wasExpanded.indexOf(path) < 0) {
				dlv.collapseRow(i);
			}
		}
	}
	private static final Comparator<FileItem> COMP = new Comparator<FileItem>() {
		@Override
		public int compare(FileItem item1, FileItem item2) {
			return compare(item1.container.path + (item1.container.binary == null ? "/" : "")
			             , item2.container.path + (item2.container.binary == null ? "/" : ""));
		}
		private int compare(String path1, String path2) {
			int index1 = path1.indexOf("/");
			int index2 = path2.indexOf("/");
			if (index1 < 0) {
				if (index2 < 0) {
					// 파일끼리 대소문자 무시하고 비교
					return path1.toUpperCase().compareTo(path2.toUpperCase());
				} else {
					// 폴더 < 파일
					return 1;
				}
			} else {
				if (index2 < 0) {
					// 파일 > 폴더
					return -1;
				} else {
					String dir1 = path1.substring(0, index1);
					String dir2 = path2.substring(0, index2);
					if (dir1.equals(dir2)) {
						// 같은 폴더면 하위 내용물 비교
						return compare(path1.substring(index1 + 1), path2.substring(index2 + 1));
					} else {
						// 폴더끼리 대소문자 무시하고 비교
						return dir1.toUpperCase().compareTo(dir2.toUpperCase());
					}
				}
			}
		}
	};
	private static String strPath(TreePath path) {
		Object[] paths = path.getPath();
		String result = paths[0].toString();
		for (int i = 1; i < paths.length; i++) {
			result += "/" + paths[i];
		}
		return result;
	}
	
	public void cd(String dir) {
		logger.info("Explorer/" + currentDir + "> cd " + dir);
		boolean isEntering = false;
		if (dir.startsWith("/")) {
			if (dir.equals("/")) {
				currentDir = "";
			} else {
				currentDir = dir.substring(1) + "/";
			}
		} else if (dir.equals("..")) {
			if (currentDir.length() > 0) {
				currentDir = currentDir.substring(0, currentDir.length() - 1);
				currentDir = currentDir.substring(0, currentDir.lastIndexOf("/") + 1);
			}
		} else if (dir.equals(".")) {
			// refresh
			openDir((currentDir.startsWith("/") ? "" : "/") + currentDir.substring(0, currentDir.length() - 1), true);
			return;
		} else {
			currentDir += dir + "/";
			isEntering = true;
		}
		while (currentDir.endsWith("//")) {
			currentDir = currentDir.substring(0, currentDir.length() - 1);
		}
		
		if (currentDir.length() == 0) {
			dlv.setSelectionRow(0);
			return;
		}
		
		if (isEntering) {
			// 폴더 엔터/더블클릭 진입일 때 내용물이 한 폴더만 있는 경우 하위 폴더 자동으로 열기
			String path = null;
			for (FileItem item : list) {
				// 공통된 상위 폴더 중 최하위를 찾음
				if (item.container.path.startsWith(currentDir)) {
					if (path == null) {
						// 파일 하나 찾음
						path = item.container.path;
						path = path.substring(0, path.lastIndexOf("/") + 1);
					} else {
						// 파일 2개 이상 찾음
						while (!item.container.path.startsWith(path)) {
							path = path.substring(0, path.length() - 1);
							path.substring(0, path.lastIndexOf("/") + 1);
						}
						if (path.equals(currentDir)) {
							// 더 찾을 게 없음
							path = null;
							break;
						}
					}
				}
			}
			if (path != null) {
				cd("/" + path);
				return;
			}
		}
		
		TreePath path = null;
		while ((path = (currentDir.length() == 0 ? pathMap.get("/") : pathMap.get(currentDir))) == null) {
			currentDir = currentDir.substring(0, currentDir.substring(0, currentDir.length() - 1).lastIndexOf('/') + 1);
		}
		dlv.setSelectionPath(path);
	}
	public void openDir(String dir, boolean byTree) {
		logger.info("Explorer.openDir: " + dir);
		if (dir.equals("/")) {
			currentDir = "";
		} else {
			currentDir = dir.substring(1) + "/";
		}
		
		int cut = currentDir.length();
		
		flModel.clear();
		if (cut > 0) {
			flModel.addElement(new FileItem(".."));
		}
		List<String> dirLabels = new ArrayList<>();
		for (FileItem item : list) {
			String path = item.container.path;
			if (cut > 0) {
				if (!path.startsWith(currentDir)) {
					// 현재 디렉토리 내용물 아니면 제외
					continue;
				}
				path = path.substring(cut);
			}
			
			if (path.indexOf("/") < 0) {
				// 파일
				//item.label = path + " (" + comma(item.container.binary.length) + ")" + (item.originalPath == null ? "" : ": " + item.originalPath);
				if (item.container.binary == null) { // 디렉토리
					if (dirLabels.contains(item.label)) {
						continue;
					}
					dirLabels.add(item.label);
				}
				flModel.addElement(item);
				
			} else {
				// 디렉토리
				String label = path.substring(0, path.indexOf("/"));
				if (!dirLabels.contains(label)) {
					dirLabels.add(label);
					flModel.addElement(new FileItem(label));
				}
			}
		}
	}
	
	@Override
	public void setTransferHandler(TransferHandler th) {
		flv.setTransferHandler(th);
	}
	@Override
	public synchronized void setDropTarget(DropTarget dt) {
		flv.setDropTarget(dt);
	}
	public void openSelectedFile() {
		logger.info("Explorer.openSelectedFile");
		FileItem item = flv.getSelectedValue();
		if (item == null) {
			return;
		}
		if (item.container == null || item.container.binary == null) {
			cd(item.label);
		} else {
			logger.info("Explorer.Lisetener.runFile: " + item.container.path);
			listener.runFile(item.container);
		}
	}
}