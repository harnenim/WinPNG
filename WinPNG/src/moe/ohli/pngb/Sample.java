package moe.ohli.pngb;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;

import javax.swing.*;

@SuppressWarnings("serial")
public class Sample extends JFrame {

	private JPanel form = new JPanel();
	
	private void init() {
		setTitle("Algorithm Sample");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		int width  = 1360;
		int height = 800;
		setBounds((screenSize.width - width) / 2, (screenSize.height - height) / 2, width, height);
		
		// 아이콘 가져오기
		setIconImage(new ImageIcon(getClass().getResource("icon.png")).getImage());
		
		setLayout(new BorderLayout());
		form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
		add(new JScrollPane(form), BorderLayout.CENTER);
		
		BufferedImage targetImage = new BufferedImage(256, 256, BufferedImage.TYPE_3BYTE_BGR);
		for (int x = 0; x < 256; x++) {
			for (int y = 0; y < 256; y++) {
				targetImage.setRGB(x, y, (x<<16)|(x<<8));
			}
		}
		BufferedImage dataImage = new BufferedImage(256, 256, BufferedImage.TYPE_3BYTE_BGR);
		for (int x = 0; x < 256; x++) {
			for (int y = 0; y < 256; y++) {
				dataImage.setRGB(x, y, (y<<16)|(y<<8));
			}
		}
		
		try {
			addSample("1:1:4 Leagacy", new Container.WithTarget(targetImage, null).toBitmapLegacy(dataImage),  1, 64, 64);
			addSample("1:1:4 v1"     , new Container.WithTarget(targetImage, null).toBitmap114v1 (dataImage),  1, 32, 32);
			addSample("1:1:4 v2"     , new Container.WithTarget(targetImage, null).toBitmap114v2 (dataImage), 16, 32, 32);
			addSample("1:1:4 v3"     , new Container.WithTarget(targetImage, null).toBitmap114v3 (dataImage),  8, 16, 16);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		// 창 띄우기
		setVisible(true);
		setEnabled(true);
	}
	
	private void addSample(String title, BufferedImage result, int ax, int bx, int cx) {
		BufferedImage[] parsed = {
				new BufferedImage(256, 256, BufferedImage.TYPE_3BYTE_BGR)
			,	new BufferedImage(256, 256, BufferedImage.TYPE_3BYTE_BGR)
			,	new BufferedImage(256, 256, BufferedImage.TYPE_3BYTE_BGR)
			,	new BufferedImage(256, 256, BufferedImage.TYPE_3BYTE_BGR)
		};
		BufferedImage[] avgs = {
				new BufferedImage(256,   2, BufferedImage.TYPE_3BYTE_BGR)
			,	new BufferedImage(256,   2, BufferedImage.TYPE_3BYTE_BGR)
			,	new BufferedImage(256,   2, BufferedImage.TYPE_3BYTE_BGR)
			,	new BufferedImage(256,   1, BufferedImage.TYPE_3BYTE_BGR)
			,	new BufferedImage(256,   2, BufferedImage.TYPE_3BYTE_BGR)
		};
		BufferedImage chart = new BufferedImage(256, 256, BufferedImage.TYPE_3BYTE_BGR);
		int a, b, c, sumA, sumB, sumC, aA, aB, aC;
		double std, sumStd;
		for (int x = 0; x < 256; x++) {
			sumA = 0;
			sumB = 0;
			sumC = 0;
			aA = 0;
			aB = 0;
			aC = 0;
			sumStd = 0;
			for (int y = 0; y < 256; y++) {
				parsed[0].setRGB(x, y, b = (result.getRGB(2*x+1, 2*y  ) & 0xFFFF00));
				parsed[1].setRGB(x, y, c = (result.getRGB(2*x  , 2*y+1) & 0xFFFF00));
				parsed[2].setRGB(x, y, a = (result.getRGB(2*x+1, 2*y+1) & 0xFFFF00));
				a >>= 16; b >>= 16; c >>= 16;
				double avg = (b + c + a) / 3.0;
				parsed[3].setRGB(x, y, ((int) Math.round(std = Math.sqrt(((b-avg)*(b-avg) + (c-avg)*(c-avg) + (a-avg)*(a-avg)) / 3) * 2)) << 16);
				sumA += a;
				sumB += b;
				sumC += c;
				sumStd += std;
				
				aB += b; aC += c; aA += a;
				int rgbB = Math.min(((chart.getRGB(x, 255-b) & 0xFF0000) >> 16) + bx, 0xFF);
				int rgbC = Math.min(((chart.getRGB(x, 255-c) & 0x0000FF) >>  0) + cx, 0xFF);
				int rgbA = Math.min(((chart.getRGB(x, 255-a) & 0x00FF00) >>  8) + ax, 0xFF);
				chart.setRGB(x, 255-b, rgbB << 16);
				chart.setRGB(x, 255-c, rgbC <<  0);
				chart.setRGB(x, 255-a, rgbA <<  8);
			}
			avgs[0].setRGB(x, 0, (sumB + 255) / 256 * 0x010100);
			avgs[1].setRGB(x, 0, (sumC + 255) / 256 * 0x010100);
			avgs[2].setRGB(x, 0, (sumA + 255) / 256 * 0x010100);
			avgs[3].setRGB(x, 0, (int) (sumStd + 255) / 256 * 0x010000);
			avgs[4].setRGB(x, 0, ((aB + 255) / 256 * 0x010000)
					           | ((aC + 255) / 256 * 0x000001)
					           | ((aA + 255) / 256 * 0x000100));
			avgs[0].setRGB(x, 1, (x / 2) * 0x010100 + 0x3F3F3F);
			avgs[1].setRGB(x, 1, (x / 2) * 0x010100 + 0x3F3F3F);
			avgs[2].setRGB(x, 1, (x / 2) * 0x010100 + 0x3F3F3F);
			avgs[4].setRGB(x, 1, (x / 2) * 0x010101 + 0x3F3F3F);
		}
		
		JPanel panel = new JPanel(new BorderLayout());
		panel.add(new JLabel(title), BorderLayout.NORTH);
		
		JPanel center = new JPanel(new BorderLayout());
		{
			JPanel view = new JPanel();
			JLabel
			label = new JLabel("↗"      ); label.setPreferredSize(new Dimension(256, 12)); view.add(label);
			label = new JLabel("↙"      ); label.setPreferredSize(new Dimension(256, 12)); view.add(label);
			label = new JLabel("↘"      ); label.setPreferredSize(new Dimension(256, 12)); view.add(label);
			label = new JLabel("표준편차"); label.setPreferredSize(new Dimension(256, 12)); view.add(label);
			label = new JLabel("값 범위" ); label.setPreferredSize(new Dimension(256, 12)); view.add(label);
			center.add(view, BorderLayout.NORTH);
			
			view = new JPanel();
			view.add(new JLabel(new ImageIcon(parsed[0])));
			view.add(new JLabel(new ImageIcon(parsed[1])));
			view.add(new JLabel(new ImageIcon(parsed[2])));
			view.add(new JLabel(new ImageIcon(parsed[3])));
			view.add(new JLabel(new ImageIcon(chart)));
			center.add(view, BorderLayout.CENTER);
			
			view = new JPanel();
			view.add(new JLabel(new ImageIcon(avgs[0].getScaledInstance(256, 20, Image.SCALE_SMOOTH))));
			view.add(new JLabel(new ImageIcon(avgs[1].getScaledInstance(256, 20, Image.SCALE_SMOOTH))));
			view.add(new JLabel(new ImageIcon(avgs[2].getScaledInstance(256, 20, Image.SCALE_SMOOTH))));
			view.add(new JLabel(new ImageIcon(avgs[3].getScaledInstance(256, 20, Image.SCALE_SMOOTH))));
			view.add(new JLabel(new ImageIcon(avgs[4].getScaledInstance(256, 20, Image.SCALE_SMOOTH))));
			center.add(view, BorderLayout.SOUTH);
		}
		panel.add(center, BorderLayout.CENTER);
		
		form.add(panel);
	}
	
	public static void main(String[] args) {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
			e.printStackTrace();
		}
		new Sample().init();
	}
}