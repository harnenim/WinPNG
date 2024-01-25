package moe.ohli.pngb;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

@SuppressWarnings("serial")
public class Explorer extends JPanel {
	private static final String TMP_DIR = System.getProperty("java.io.tmpdir").replace('\\', '/') + "WinPNG/";
	private static final Color SELECTED_COLOR = new Color(204, 232, 255);
	
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
    
	public static class FileItem {
		public Container container;
		public String originalPath;
		public String label;
		public FileItem(Container container, String originalPath) {
			this.container = container;
			this.originalPath = originalPath;
			this.label = null;
		}
		public FileItem(String label) {
			this.label = label;
		}
		/**
		 * 리스트뷰에 출력할 텍스트
		 */
		@Override
		public String toString() {
			return (container == null ? "📁" : "") + label;
		}
	}
	public static interface Listener {
		public void runFile(Container cont);
		public void checkPw();
		public void deleteSelected();
	}
	
	private static class DirTreeNode extends DefaultMutableTreeNode {
		public DirTreeNode(Object arg0) {
			super(arg0);
		}
		public DirTreeNode findNode(String name) {
			System.out.println("findNode: " + name + " from " + getUserObject());
			@SuppressWarnings("unchecked")
			Enumeration<DefaultMutableTreeNode> children = children();
			while (children.hasMoreElements()) {
				DirTreeNode child = (DirTreeNode) children.nextElement();
				System.out.println("child: " + child.getUserObject());
				if (name.equals(child.getUserObject())) {
					return child;
				}
			}
			return null;
		}
	}
	
	private List<FileItem> list = new ArrayList<>();
	private Logger logger;
	private Listener listener;
	
	private DirTreeNode dlRoot = new DirTreeNode("/");
	private JTree dlv = new JTree(dlRoot);

	private DefaultListModel<FileItem> flModel = new DefaultListModel<>();
	private JList<FileItem> flv = new JList<>(flModel);
	
	private String currentDir = "";
	public String getDir() {
		return currentDir;
	}
	
	public Explorer(Logger logger, Listener listener) {
		super(new BorderLayout());
		this.logger = logger;
		this.listener = listener;
		
		JScrollPane spd = new JScrollPane(dlv);
		JScrollPane spf = new JScrollPane(flv);
		add(spd, BorderLayout.WEST);
		add(spf, BorderLayout.CENTER);
		
		spd.setPreferredSize(new Dimension(120, 0));
		
		DefaultTreeCellRenderer renderer = (DefaultTreeCellRenderer) dlv.getCellRenderer();
		Icon dIcon = renderer.getClosedIcon();
		Icon fIcon = renderer.getLeafIcon();
		renderer.setLeafIcon(dIcon);
		renderer.setBackgroundSelectionColor(SELECTED_COLOR);
		
		flv.setCellRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean expanded) {
				FileItem item = (FileItem) value;
				JLabel label = new JLabel(item.label);
				label.setOpaque(true);
				label.setIcon(item.container == null ? dIcon : fIcon);
				if (selected) {
					label.setBackground(SELECTED_COLOR);
				} else {
					label.setBackground(Color.WHITE);
				}
				return label;
			}
		});
		
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
				cd(path, true);
			}
		});

		flv.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		flv.setDragEnabled(true);
		
		// 더블 클릭 실행
		flv.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent evt) {
				logger.info("mouseClicked");
				if (evt.getClickCount() == 2) {
					Explorer.this.logger.debug("더블 클릭");
					openSelectedFile();
				}
				if (evt.getButton() == MouseEvent.BUTTON3) {
					logger.debug("우클릭");
					Explorer.this.listener.checkPw();
				}
			}
		});
		flv.addKeyListener(new KeyListener() {
			@Override
			public void keyTyped(KeyEvent arg0) {}
			
			@Override
			public void keyReleased(KeyEvent e) {
				switch (e.getKeyCode()) {
					case 27: { // ESC
						clearSelection();
						break;
					}
					case 127: { // Delete
						listener.deleteSelected();
						break;
					}
				}
			}
			
			@Override
			public void keyPressed(KeyEvent e) {
				switch (e.getKeyCode()) {
					case 10: { // Enter
						openSelectedFile();
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
	
	        @Override
	        public int getSourceActions(JComponent c) {
	            return MOVE;
	        }
	    });
	}
	@Override
	public synchronized void addKeyListener(KeyListener kl) {
		flv.addKeyListener(kl);
	}
	public void openSelectedFile() {
		logger.info("openSelectedFile");
		FileItem item = flv.getSelectedValue();
		if (item == null) {
			return;
		}
		if (item.container == null) {
			logger.info("openDir: " + item.label);
			cd(item.label);
		} else {
			logger.info("run: " + item.container.path);
			listener.runFile(item.container);
		}
	}
	public boolean isEmpty() {
		return list.isEmpty();
	}
	public void clear() {
		list.clear();
		dlRoot.removeAllChildren();
		flModel.clear();
	}
	public void add(FileItem item) {
		logger.info("add: " + item.container.path);
		list.add(item);
		refresh();
	}
	public boolean remove(FileItem item) {
		logger.info("remove: " + item.container.path);
		if (list.remove(item)) {
			refresh();
			return true;
		}
		return false;
	}
	public void removeSelected() {
		logger.info("removeSelected");
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
		refresh();
	}
	public void selectAll() {
		logger.info("selectAll");
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
		logger.info("clearSelection");
		flv.clearSelection();
	}
	public List<Container> getAllContainers() {
		List<Container> result = new ArrayList<>();
		for (FileItem item : list) {
			result.add(item.container);
		}
		return result;
	}
	public List<FileItem> getDirItems() {
		List<FileItem> result = new ArrayList<>();
		for (FileItem subItem : list) {
			if (currentDir.length() == 0 || subItem.container.path.startsWith(currentDir)) {
				result.add(subItem);
			}
		}
		return result;
	}
	public List<Container> getSelectedContainers() {
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
	
	@Override
	public void setTransferHandler(TransferHandler th) {
		flv.setTransferHandler(th);
	}
	
	@Override
	public synchronized void setDropTarget(DropTarget dt) {
		flv.setDropTarget(dt);
	}
	
	public void setContainers(List<Container> containers) {
		list.clear();
		for (Container cont : containers) {
			list.add(new FileItem(cont, null));
		}
		refresh();
	}
	public void refresh() {
		sort();
		refreshTree();
		cd("/" + currentDir);
		
		while (flModel.size() == 1 && currentDir.length() > 0) {
			cd("..");
		}
	}
	public void sort() {
		Collections.sort(list, COMP);
	}
	private Map<String, TreePath> pathMap = new HashMap<>();
	public void refreshTree() {
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
			return compare(item1.container.path.replace('\\', '/'), item2.container.path.replace('\\', '/'));
		}
		private int compare(String path1, String path2) {
			int index1 = path1.indexOf("/");
			int index2 = path2.indexOf("/");
			if (index1 < 0) {
				if (index2 < 0) {
					return path1.compareTo(path2);
				} else {
					return 1;
				}
			} else {
				if (index2 < 0) {
					return -1;
				} else {
					String dir1 = path1.substring(0, index1);
					String dir2 = path2.substring(0, index2);
					if (dir1.equals(dir2)) {
						return compare(path1.substring(index1 + 1), path2.substring(index2 + 1));
					} else {
						return dir1.compareTo(dir2);
					}
				}
			}
		}
	};
	
	public void cd(String dir) {
		cd(dir, false);
	}
	public void cd(String dir, boolean byTree) {
		System.out.println("cd: " + dir);
		if (dir.startsWith("/")) {
			if (dir.equals("/")) {
				this.currentDir = "";
			} else {
				this.currentDir = dir.substring(1) + "/";
			}
		} else if (dir.equals("..")) {
			this.currentDir = this.currentDir.substring(0, this.currentDir.length() - 1);
			this.currentDir = this.currentDir.substring(0, this.currentDir.lastIndexOf("/") + 1);
		} else {
			this.currentDir += dir + "/";
		}
		while (this.currentDir.endsWith("//")) {
			this.currentDir = this.currentDir.substring(0, this.currentDir.length() - 1);
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
				if (item.label == null) {
					item.label = path + " (" + comma(item.container.binary.length) + ")" + (item.originalPath == null ? "" : ": " + item.originalPath);
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
		if (byTree) return;
		
		if (cut == 0) {
			dlv.setSelectionRow(0);
			return;
		}
		dlv.setSelectionPath(pathMap.get(this.currentDir));
	}
	
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
}