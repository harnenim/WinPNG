package moe.ohli.pngb;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

import javax.swing.*;
import javax.swing.border.Border;
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
	private static final String TMP_DIR = System.getProperty("java.io.tmpdir").replace('\\', '/') + "WinPNG/";
	private static final Color SELECTED_COLOR = new Color(204, 232, 255);
	private final Border FOCUS_BORDER = UIManager.getBorder("List.focusCellHighlightBorder");
	
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
		}
		public FileItem(Container container, String originalPath) {
			this.container = container;
			this.originalPath = originalPath;
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
			if (container == null) return; // 디렉토리는 건드릴 일 없음

			label = container.path.substring(container.path.lastIndexOf('/') + 1)
					+ " (" + comma(container.binary.length) + ")"
					+ (originalPath == null ? "" : ": " + originalPath);
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
		public void onUpdate();
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
	
	private List<FileItem> list = new ArrayList<>();
	private Logger logger;
	private Listener listener;
	private Font font;
	
	private DirTreeNode dlRoot = new DirTreeNode("/");
	private JTree dlv = new JTree(dlRoot);

	private DefaultListModel<FileItem> flModel = new DefaultListModel<>();
	private JList<FileItem> flv = new RubberBandList<>(flModel);
	
	private String currentDir = "";
	public String getDir() {
		return currentDir;
	}
	
	/**
	 * 탐색기 생성자
	 * @param logger
	 * @param listener
	 */
	public Explorer(Logger logger, Listener listener) {
		super(new BorderLayout());
		this.logger = logger;
		this.listener = listener;
		
		{	// UI 배치
			JScrollPane spd = new JScrollPane(dlv);
			JScrollPane spf = new JScrollPane(flv);
			add(spd, BorderLayout.WEST);
			add(spf, BorderLayout.CENTER);
			
			spd.setPreferredSize(new Dimension(120, 0));

			dlv.setRowHeight(19);
			flv.setFixedCellHeight(19);
		}
		
		{	// 폴더/파일 아이콘
			DefaultTreeCellRenderer renderer = (DefaultTreeCellRenderer) dlv.getCellRenderer();
			Icon closedIcon = renderer.getClosedIcon();
			Icon fIcon = renderer.getLeafIcon();
			try {
				closedIcon = FileSystemView.getFileSystemView().getSystemIcon(new File(TMP_DIR));
				renderer.setClosedIcon(closedIcon);
				renderer.setOpenIcon(closedIcon);
			} catch (Exception e) {
				logger.debug(e);
			}
			final Icon dIcon = closedIcon;
			renderer.setLeafIcon(closedIcon);
			renderer.setBackgroundSelectionColor(SELECTED_COLOR);

			font = renderer.getFont();
			String os = System.getProperty("os.name");
			if (os.toLowerCase().startsWith("windows")) {
				font = new Font("맑은 고딕", Font.PLAIN, 12);
				renderer.setFont(font);
			}
			
			flv.setCellRenderer(new DefaultListCellRenderer() {
				@Override
				public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean hasFocus) {
					FileItem item = (FileItem) value;
					Icon icon = item.container == null ? dIcon : fIcon;
					if (item.container != null) {
						try {
							Icon systemIcon = FileSystemView.getFileSystemView().getSystemIcon(File.createTempFile(TMP_DIR + "icon", item.getName()));
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
					label.setFont(font);
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
					logger.info("mouseClicked");
					if (evt.getClickCount() == 2) {
						if (evt.getButton() == MouseEvent.BUTTON3) {
							logger.debug("우측 더블 클릭");
							listener.requestCheckError(); // 파싱 오류 보완책
						} else {
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
							if (e.isAltDown()) {
								cd("..");
							}
						}
					}
				}
			});
			flv.setTransferHandler(new TransferHandler() {
				@Override
				protected Transferable createTransferable(JComponent c) {
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
						String primaryName = subFile.getAbsolutePath().substring(rootDir.length() + 1);
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
				
				@Override
				public int getSourceActions(JComponent c) {
					return MOVE;
				}
			});
		}
	}
	@Override
	public synchronized void addKeyListener(KeyListener kl) {
		flv.addKeyListener(kl);
	}
	
	public boolean isEmpty() {
		return list.isEmpty();
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
	public void removeSelected() {
		removeSelected(true);
	}
	public void removeSelected(boolean withUpdate) {
		logger.info("Explorer.removeSelected");
		List<FileItem> removeList = new ArrayList<>();
		for (FileItem item : flv.getSelectedValuesList()) {
			if (item.container == null) {
				String subDir = currentDir + item.label + "/";
				for (FileItem subItem : list) {
					if (subItem.container.path.startsWith(subDir)) {
						removeList.add(subItem);
					}
				}
			} else {
				removeList.add(item);
			}
		}
		for (FileItem item : removeList) {
			list.remove(item);
		}
		refresh(withUpdate);
	}
	private static final char[] antiChars = "\\/:*?\"<>|".toCharArray();
	public static boolean isValidFileName(String name) {
		if (name == null || name.length() == 0) {
			return false;
		}
		for (char c : name.toCharArray()) {
			for (char anti : antiChars) {
				if (c == anti) {
					return false;
				}
			}
		}
		return true;
	}
	public void renameSelected() {
		logger.info("Explorer.renameSelected");
		FileItem selected = flv.getSelectedValue();
		if (selected == null) return;
		
		String name = selected.label; // 디렉토리
		if (selected.container != null) { // 파일
			name = selected.container.path.substring(selected.container.path.lastIndexOf('/') + 1);
		}
		name = JOptionPane.showInputDialog(this, Strings.get("새 이름을 입력하세요."), name);
		logger.info("name: " + name);
		
		if (!isValidFileName(name)) {
			if (name != null) {
				JOptionPane.showMessageDialog(this, Strings.get("올바른 이름이 아닙니다."));
			}
			return;
		}
		
		if (selected.container == null) {
			// 디렉토리
			String from = currentDir + selected.label + "/";
			String to = currentDir + name + "/";
			for (FileItem item : list) {
				if (item.container.path.startsWith(from)) {
					item.container.path = to + item.container.path.substring(from.length());
				}
			}
			refresh(true);
			
		} else {
			// 파일
			selected.container.path = selected.container.path.substring(0, selected.container.path.lastIndexOf('/') + 1) + name;
			selected.refreshLabel();
			sort();
			cd(".");
			listener.onUpdate();
		}
	}
	public void selectAll() {
		logger.info("Explorer.selectAll");
		FileItem firstItem = flModel.get(0);
		if (firstItem == null) {
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
			result.add(item.container);
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
			if (item.container == null) {
				// 디렉토리일 경우 하위 모든 파일 선택
				String subDir = currentDir + item.label + "/";
				for (FileItem subItem : list) {
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
		logger.info("Explorer.setContainers" + (withUpdate ? " with update" : ""));
		list.clear();
		for (Container cont : containers) {
			cont.path = cont.path.replace('\\', '/');
			list.add(new FileItem(cont));
		}
		refresh(withUpdate);
	}
	public void setContainers(List<Container> containers, String name, boolean withUpdate) {
		logger.info("Explorer.setContainers " + name + (withUpdate ? " with update" : ""));
		list.clear();
		for (Container cont : containers) {
			cont.path = cont.path.replace('\\', '/');
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
		logger.info("Explorer.refresh" + (withUpdate ? " with update" : ""));
		sort();
		refreshTree();
		cd("/" + currentDir);
		
		while (flModel.size() == 1 && currentDir.length() > 0) {
			cd("..");
		}
		
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
		logger.info("Explorer.refreshTree");
		dlRoot.removeAllChildren();
		pathMap.clear();
		List<String> paths = new ArrayList<String>();
		paths.add("/");
		
		for (FileItem item : list) {
			DirTreeNode parent = dlRoot;
			String[] names = item.container.path.split("/");
			String path = "";
			for (int i = 0; i < names.length - 1; i++) {
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
		
		List<Boolean> wasExpandedList = new ArrayList<Boolean>();
		for (int i = 0; i < paths.size(); i++) {
			wasExpandedList.add(dlv.isExpanded(i));
			dlv.expandRow(i);
			
			pathMap.put(paths.get(i), dlv.getPathForRow(i));
		}
		for (int i = wasExpandedList.size() - 1; i > 0; i--) {
			if (!wasExpandedList.get(i)) {
				dlv.collapseRow(i);
			}
		}
	}
	private static final Comparator<FileItem> COMP = new Comparator<FileItem>() {
		@Override
		public int compare(FileItem item1, FileItem item2) {
			return compare(item1.container.path, item2.container.path);
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
	
	public void cd(String dir) {
		logger.info("Explorer/" + currentDir + "> cd " + dir);
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
			openDir(currentDir.length() == 0 ? "/" : currentDir.substring(0, currentDir.length() - 1), true);
			return;
		} else {
			currentDir += dir + "/";
		}
		while (currentDir.endsWith("//")) {
			currentDir = currentDir.substring(0, currentDir.length() - 1);
		}
		
		if (currentDir.length() == 0) {
			dlv.setSelectionRow(0);
			return;
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
			this.currentDir = "";
		} else {
			this.currentDir = dir.substring(1) + "/";
		}
		
		int cut = this.currentDir.length();
		
		flModel.clear();
		if (cut > 0) {
			flModel.addElement(new FileItem(".."));
		}
		List<String> dirLabels = new ArrayList<>();
		for (FileItem item : list) {
			String path = item.container.path;
			if (cut > 0) {
				if (!path.startsWith(this.currentDir)) {
					// 현재 디렉토리 내용물 아니면 제외
					continue;
				}
				path = path.substring(cut);
			}
			
			if (path.indexOf("/") < 0) {
				// 파일
				item.label = path + " (" + comma(item.container.binary.length) + ")" + (item.originalPath == null ? "" : ": " + item.originalPath);
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
		if (item.container == null) {
			cd(item.label);
		} else {
			logger.info("Explorer.Lisetener.runFile: " + item.container.path);
			listener.runFile(item.container);
		}
	}
}