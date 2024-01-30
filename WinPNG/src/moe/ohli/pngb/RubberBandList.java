package moe.ohli.pngb;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.util.*;
import java.util.stream.IntStream;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

@SuppressWarnings("serial")
class RubberBandList<E> extends JList<E> {
	private Runnable lastUpdateRunnable = null;
	private List<E> lastSelectedValuesList = new ArrayList<>();
	private boolean using = false;
	private Point fromPoint = new Point();
	private final Path2D rubberBand = new Path2D.Double();
	
	protected RubberBandList(ListModel<E> model) {
		super(model);
		setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		
		MouseAdapter ma = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				Point pt = e.getPoint();
				
				// 마우스 누른 위치 상태 확인
				int index = locationToIndex(pt);
				Rectangle cellBounds = getCellBounds(index, index);
				if (cellBounds != null) {
					if (getCellBounds(index, index).contains(pt)) {
						E selectedValue = getSelectedValue();
						if (selectedValue != null) {
							for (E lastSelectedValue : lastSelectedValuesList) {
								if (selectedValue == lastSelectedValue) {
									// 기존에 선택돼있던 거면 기본 드래그 동작
									return;
								}
							}
						}
					}
				}
				
				// 선택되지 않았던 곳에서 드래그하면 러버밴드 사용 시작
				using = true;
				setDragEnabled(false);
				clearSelection(); // 여백 눌렀는데 마지막 게 선택돼있는 경우 해제
				lastUpdateRunnable = null;
				fromPoint = pt;
				
				mouseDragged(e);
			}
			@Override
			public void mouseDragged(MouseEvent e) {
				if (!using) return;
				
				// 커서 위치대로 러버밴드 그리기
				Point destPoint = e.getPoint();
				rubberBand.reset();
				rubberBand.moveTo(fromPoint.x, fromPoint.y);
				rubberBand.lineTo(destPoint.x, fromPoint.y);
				rubberBand.lineTo(destPoint.x, destPoint.y);
				rubberBand.lineTo(fromPoint.x, destPoint.y);
				rubberBand.closePath();
				repaint();

				// 러버밴드 기준으로 항목 선택하기
				int[] indices = IntStream.range(0, getModel().getSize()).filter(i -> rubberBand.intersects(getCellBounds(i, i))).toArray();
				setSelectedIndices(indices); // 여기선 valueChanged가 작동하지 않으므로
				lastSelectedValuesList = getSelectedValuesList(); // 여기서 바꿔줌
			}
			@Override
			public void mouseReleased(MouseEvent e) {
				if (!using) return;
				
				// 러버밴드 사용 종료
				rubberBand.reset();
				repaint();
				setDragEnabled(true);
				using = false;
			}
		};
		addMouseListener(ma);
		addMouseMotionListener(ma);
		
		// Rubber Band 생성 직전 상태를 기억해야 함
		addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent evt) {
				if (using) {
					return;
				}
				final List<E> selectedValuesList = getSelectedValuesList();
				new Thread(lastUpdateRunnable = new Runnable() {
					@Override
					public void run() {
						// 선택 직후에 과거값이 남아있도록 딜레이를 줌
						try {
							Thread.sleep(1);
						} catch (Exception e) {
							e.printStackTrace();
						}
						// 가장 최신 스레드만 동작시킴
						if (lastUpdateRunnable != this) {
							return;
						}
						lastSelectedValuesList = selectedValuesList;
					}
				}).start();
			}
		});
	}
	
	// 색상 교체 지원..까지 개발하는 건 너무 멀리 가는 건가...
	private Color rbBorderColor = new Color(0, 120, 215);
	private Color rubberBandColor = new Color(0, 103, 204, 85);

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setPaint(rbBorderColor);
		g2.draw(rubberBand);
		g2.setPaint(rubberBandColor);
		g2.fill(rubberBand);
		g2.dispose();
	}
}