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
		int width  = 1100;
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
			addSample("1:1:4 Leagacy", new Container.WithTarget(targetImage, null).toBitmapLegacy(dataImage));
			addSample("1:1:4 v1", new Container.WithTarget(targetImage, null).toBitmap114v1(dataImage));
			addSample("1:1:4 v2", new Container.WithTarget(targetImage, null).toBitmap114v2(dataImage));
			addSample("1:1:4 v3", new Container.WithTarget(targetImage, null).toBitmap114v3(dataImage));
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		// 창 띄우기
		setVisible(true);
		setEnabled(true);
	}
	
	private void addSample(String title, BufferedImage result) {
		BufferedImage[] parsed = {
				new BufferedImage(256, 256, BufferedImage.TYPE_3BYTE_BGR)
			,	new BufferedImage(256, 256, BufferedImage.TYPE_3BYTE_BGR)
			,	new BufferedImage(256, 256, BufferedImage.TYPE_3BYTE_BGR)
			,	new BufferedImage(256, 256, BufferedImage.TYPE_3BYTE_BGR)
		};
		BufferedImage[] avgs = {
				new BufferedImage(256,   1, BufferedImage.TYPE_3BYTE_BGR)
			,	new BufferedImage(256,   1, BufferedImage.TYPE_3BYTE_BGR)
			,	new BufferedImage(256,   1, BufferedImage.TYPE_3BYTE_BGR)
			,	new BufferedImage(256,   1, BufferedImage.TYPE_3BYTE_BGR)
		};
		int a, b, c, sumA, sumB, sumC;
		double std, sumStd;
		for (int x = 0; x < 256; x++) {
			sumA = 0;
			sumB = 0;
			sumC = 0;
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
			}
			avgs[0].setRGB(x, 0, (sumB + 255) / 256 * 0x010100);
			avgs[1].setRGB(x, 0, (sumC + 255) / 256 * 0x010100);
			avgs[2].setRGB(x, 0, (sumA + 255) / 256 * 0x010100);
			avgs[3].setRGB(x, 0, (int) (sumStd + 255) / 256 * 0x010000);
		}
		
		JPanel panel = new JPanel(new BorderLayout());
		panel.add(new JLabel(title), BorderLayout.NORTH);
		
		JPanel view = new JPanel();
		view.add(new JLabel(new ImageIcon(parsed[0])));
		view.add(new JLabel(new ImageIcon(parsed[1])));
		view.add(new JLabel(new ImageIcon(parsed[2])));
		view.add(new JLabel(new ImageIcon(parsed[3])));
		panel.add(view, BorderLayout.CENTER);
		
		view = new JPanel();
		view.add(new JLabel(new ImageIcon(avgs[0].getScaledInstance(256, 20, Image.SCALE_SMOOTH))));
		view.add(new JLabel(new ImageIcon(avgs[1].getScaledInstance(256, 20, Image.SCALE_SMOOTH))));
		view.add(new JLabel(new ImageIcon(avgs[2].getScaledInstance(256, 20, Image.SCALE_SMOOTH))));
		view.add(new JLabel(new ImageIcon(avgs[3].getScaledInstance(256, 20, Image.SCALE_SMOOTH))));
		panel.add(view, BorderLayout.SOUTH);
		
		form.add(panel);
	}
	
	public static void main(String[] args) {
		new Sample().init();
	}
}