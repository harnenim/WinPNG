package moe.ohli.pngb;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.*;

import javax.imageio.ImageIO;

/**
 * 
 * @author harne_
 *
 */
public class Container {
	private static Logger logger = new Logger();
	public static void setLogger(Logger logger) {
		Container.logger = logger;
	}

	private static String pad(int v, int len) {
		return pad("" + v, len);
	}
	private static String pad(String str, int len) {
		while (str.length() < len) {
			str = " " + str;
		}
		return str;
	}
	
	public String originalPath; // 원래 파일 경로: 리스트뷰에서만 쓰임
	public String path;   // 상대경로
	public byte[] binary; // 파일 내용물
	
	/**
	 * 파일에서 생성
	 * @param path
	 * @param file
	 * @throws Exception
	 */
	public Container(String path, File file) throws Exception {
		logger.info(path);
		if (!file.isFile()) {
			throw new Exception("파일이 아닙니다.");
		}
		if (file.length() > 10485760) {
			throw new Exception("10MB를 초과합니다");
		}
		
		this.originalPath = file.getAbsolutePath();
		this.path = path;
		this.binary = new byte[(int) file.length()];
		
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(file);
			fis.read(this.binary);
		} catch (Exception e) {
			throw e;
		} finally {
			if (fis != null) try { fis.close(); } catch (Exception e2) { }
		}
	}
	
	/**
	 * 바이너리에서 생성
	 * @param bytes
	 */
	public Container(byte[] bytes) {
		this(bytes, 0);
	}
	/**
	 * 바이너리에서 생성
	 * @param bytes
	 * @param offset: 특정 위치부터 읽어오기
	 */
	public Container(byte[] bytes, int offset) {
		int pathLength   = threeBytesToInt(bytes, offset);
		int binaryLength = threeBytesToInt(bytes, offset + 4);
		try {
			offset += 8;
			this.path = new String(Arrays.copyOfRange(bytes, offset, offset + pathLength), "UTF-8");
		} catch (Exception e) {
			logger.warn("파일명 읽기 오류");
			logger.debug(e);
		}
		offset += ((pathLength + 3) / 4 * 4);
		this.binary = Arrays.copyOfRange(bytes, offset, offset + binaryLength);
	}
	/**
	 * 이미지 픽셀 RGB 값에서 생성
	 * @param rgbs
	 * @throws Exception
	 */
	public Container(int[] rgbs) throws Exception {
		int offset = 0;
		int pathLength   = 0xFFFFFF & rgbs[offset++];
		int binaryLength = 0XFFFFFF & rgbs[offset++];

		try {
			if (((rgbs[0] >> 24) & 0xFF) == 0xFF) {
				// RGB
				byte[] bytes = new byte[pathLength];
				for (int i = 0; i < pathLength; i++) {
					bytes[i] = (byte) ((rgbs[offset + (i/3)] >> (8 * (2-(i%3)))) & 0xFF);
				}
				this.path = new String(bytes, "UTF-8");
				offset += (pathLength + 2) / 3;
				
				this.binary = new byte[binaryLength];
				for (int i = 0; i < binaryLength; i++) {
					this.binary[i] = (byte) ((rgbs[offset + (i/3)] >> (8 * (2-(i%3)))) & 0xFF);
				}
				
			} else {
				// RGBA: 처음 개발했던 RGBA에 대해 레거시 지원
				byte[] bytes = new byte[pathLength];
				for (int i = 0; i < pathLength; i++) {
					bytes[i] = (byte) ((rgbs[offset + (i/4)] >> (8 * (3-(i%4)))) & 0xFF);
				}
				this.path = new String(bytes, "UTF-8");
				offset += (pathLength + 3) / 4;
				
				this.binary = new byte[binaryLength];
				for (int i = 0; i < binaryLength; i++) {
					this.binary[i] = (byte) ((rgbs[offset + (i/4)] >> (8 * (3-(i%4)))) & 0xFF);
				}
				
			}
			
		} catch (Exception e) {
			logger.warn("이미지 해석 오류");
			logger.debug(e);
		}
	}
	
	/**
	 * 경로명 byte 배열 반환
	 * @return
	 */
	private byte[] getPathBytes() {
		try {
			return path.getBytes("UTF-8");
		} catch (Exception e) {
			logger.error(e);
		}
		return new byte[0];
	}
	
	/**
	 * RGB로 표현할 때 필요한 픽셀 개수
	 * @return
	 */
	private int getRGBPixelCount() {
		return 2 + ((getPathBytes().length + 2) / 3) + ((binary.length + 2) / 3);
	}
	/**
	 * 특정 폭에 맞춰 직사각형을 구성할 RGB 픽셀 배열 반환
	 * @param width
	 * @return
	 */
	private int[] toRGBs(int width) {
		byte[] pathBytes = getPathBytes();
		int[] rgbs = new int[(getRGBPixelCount() + width - 1) / width * width];
		int offset = 0;
		
		rgbs[offset++] = pathBytes.length;
		rgbs[offset++] = binary.length;
		
		int i = 0;
		for (; i < pathBytes.length / 3; i++) {
			rgbs[offset++] = threeBytesToInt(pathBytes, i * 3);
		}
		if (pathBytes.length % 3 > 0) {
			byte[] last = new byte[] { pathBytes[i*3], 0, 0 };
			if (pathBytes.length % 3 > 1) {
				last[1] = pathBytes[i*3+1];
			}
			rgbs[offset++] = threeBytesToInt(last, 0);
		}
		
		i = 0;
		for (; i < binary.length / 3; i++) {
			rgbs[offset++] = threeBytesToInt(binary, i * 3);
		}
		if (binary.length % 3 > 0) {
			byte[] last = new byte[] { binary[i*3], 0, 0 };
			if (binary.length % 3 > 1) {
				last[1] = binary[i*3+1];
			}
			rgbs[offset++] = threeBytesToInt(last, 0);
		}
		
		return rgbs;
	}
	
	@Deprecated
	private int getRGBAPixelCount() {
		return 2 + ((getPathBytes().length + 3) / 4) + ((binary.length + 3) / 4);
	}
	@SuppressWarnings("unused")
	@Deprecated
	private int[] toRGBAs(int width) {
		byte[] pathBytes = getPathBytes();
		int[] argbs = new int[(getRGBAPixelCount() + width - 1) / width * width];
		int offset = 0;
		
		argbs[offset++] = pathBytes.length;
		argbs[offset++] = binary.length;

		int i = 0;
		for (; i < pathBytes.length / 4; i++) {
			argbs[offset++] = bytesToInt(pathBytes, i * 4);
		}
		if (pathBytes.length % 4 > 0) {
			byte[] last = new byte[] { pathBytes[i*4], 0, 0, 0 };
			if (pathBytes.length % 4 > 1) {
				last[1] = pathBytes[i*4+1];
				if (pathBytes.length % 4 > 2) {
					last[2] = pathBytes[i*4+2];
				}
			}
			argbs[offset++] = bytesToInt(last, 0);
		}
		
		i = 0;
		for (; i < binary.length / 4; i++) {
			argbs[offset++] = bytesToInt(binary, i * 4);
		}
		if (binary.length % 4 > 0) {
			byte[] last = new byte[] { binary[i*4], 0, 0, 0 };
			if (binary.length % 4 > 1) {
				last[1] = binary[i*4+1];
				if (binary.length % 4 > 2) {
					last[2] = binary[i*4+2];
				}
			}
			argbs[offset++] = bytesToInt(last, 0);
		}
		
		return argbs;
	}
	
	/**
	 * 4바이트를 int로 변환
	 * @param bytes
	 * @param offset
	 * @return
	 */
	private static int bytesToInt(byte[] bytes, int offset) {
		return (((int) bytes[offset + 0]) & 0xFF) << 24
		    |  (((int) bytes[offset + 1]) & 0xFF) << 16
		    |  (((int) bytes[offset + 2]) & 0xFF) <<  8
		    |  (((int) bytes[offset + 3]) & 0xFF);
	}
	/**
	 * 3바이트를 int로 변환
	 * @param bytes
	 * @param offset
	 * @return
	 */
	private static int threeBytesToInt(byte[] bytes, int offset) {
		return (((int) bytes[offset + 0]) & 0xFF) << 16
		    |  (((int) bytes[offset + 1]) & 0xFF) <<  8
		    |  (((int) bytes[offset + 2]) & 0xFF);
	}
	
	/**
	 * 리스트뷰에 출력 텍스트
	 */
	@Override
	public String toString() {
		return path + "(" + binary.length + ")" + (originalPath == null ? "" : ": " + originalPath);
	}
	
	/**
	 * 파일로 저장
	 * @param dirPath
	 * @return
	 */
	public File toFile(String dirPath) {
		logger.info("\nContainer.toFile: " + path);
		File file = null;
		FileOutputStream fos = null;
		try {
			String filePath = dirPath + "/" + path;
			file = new File(filePath);
			String absolutePath = file.getAbsolutePath();
			logger.debug("absolute path: " + absolutePath);
			
			int last = Math.max(absolutePath.replace('\\', '/').lastIndexOf("/"), 0);
			File dir = new File(absolutePath.substring(0, last));
			dir.mkdirs();
			
			fos = new FileOutputStream(file);
			fos.write(binary);
			
		} catch (Exception e) {
			logger.warn("파일로 저장 실패: " + dirPath);
			logger.debug(e);
			file = null;
			
		} finally {
			if (fos != null) try { fos.close(); } catch (Exception e) { }
		}
		return file;
	}
	
	/**
	 * 파일/디렉토리를 컨테이너 목록으로 변환
	 * @param file: 파일/디렉토리
	 * @param allows: 허용된 확장자(개발X)
	 * @return 컨테이너 목록
	 * @throws Exception
	 */
	public static List<Container> fileToContainers(File file) throws Exception {
		List<Container> containers = new ArrayList<Container>();
		fileToContainers(containers, "", file);
		return containers;
	}
	/**
	 * 파일/디렉토리를 컨테이너 목록에 추가
	 * @param containers: 컨테이너 목록
	 * @param path: 상위 디렉토리 경로
	 * @param file: 하위 파일/디렉토리
	 * @throws Exception
	 */
	private static void fileToContainers(List<Container> containers, String path, File file) throws Exception {
		if (file.isDirectory()) {
			String subPath = path + file.getName() + "/";
			File[] files = file.listFiles();
			for (File subFile : files) {
				try {
					fileToContainers(containers, subPath, subFile);
				} catch (Exception e) {
					throw e;
				}
			}
		} else {
			if (file.isFile()) {
				try {
					containers.add(new Container(path + file.getName(), file));
				} catch (Exception e) {
					logger.warn("파일 컨테이너 생성 실패");
					logger.debug(e);
				}
			}
		}
	}
	
	/**
	 * 컨테이너 목록에서 파일 생성
	 * @param containers: 컨테이너 목록
	 * @param dirPath: 파일을 생성할 디렉토리
	 * @return 생성된 파일 목록
	 */
	public static List<File> containersToFiles(List<Container> containers, String dirPath) {
		logger.info("\nContainer.containersToFiles: " + dirPath);
		List<File> files = new ArrayList<File>();
		for (Container cont : containers) {
			File file = cont.toFile(dirPath);
			if (file != null) {
				files.add(file);
			}
		}
		return files;
	}

	/**
	 * 비트맵 이미지가 가질 크기를 구한다
	 * @param containers
	 * @return 배율
	 */
	private static int getRatio(List<Container> containers) throws Exception {
		// 필요한 크기 계산(부정확함)
		int size = 0;
		for (Container cont : containers) {
			size += cont.getRGBPixelCount();
		}
		if (size > 20971520) {
			throw new Exception("20MB를 초과합니다");
		}
		
		// 16:9 비율을 타겟으로 함
		return (int) Math.round(Math.sqrt(size / 36.0)); // 36 = ((16/₂) x (9/₂))
	}
	/**
	 * 컨테이너 목록을 이미지로 변환
	 * @param containers: 컨테이너 목록
	 * @return: 비트맵 이미지
	 * @throws Exception
	 */
	public static BufferedImage toBitmap(List<Container> containers) throws Exception {
		return toBitmap(containers, 0);
	}
	/**
	 * 컨테이너 목록을 이미지로 변환
	 * @param containers: 컨테이너 목록
	 * @param minWidth: 최소 폭
	 * @return: 비트맵 이미지
	 * @throws Exception
	 */
	public static BufferedImage toBitmap(List<Container> containers, int minWidth) throws Exception {
		logger.info("\nContainer.toBitmap");
		// 최종 이미지 크기 구하기
		int ratio = getRatio(containers);
		int width = Math.max(minWidth, 8 * ratio);
		int contCount = containers.size();
		int containersHeight = 0;
		for (Container cont : containers) {
			containersHeight += (cont.getRGBPixelCount() + width - 1) / width;
		}
		logger.info("max(" + (width * 9 / 16) + ", " + containersHeight + ")");
		int height = Math.max(width * 9 / 16, containersHeight);
		logger.info("output size: " + width + " x " + height);
		
		// 정크 영역 랜덤 배분
		int[] dividers = new int[contCount + 1];
		{
			int junkHeight = height - containersHeight;
			logger.info("junkHeight: " + junkHeight);
			for (int i = 0; i < contCount; i++) {
				dividers[i] = (int) (junkHeight * Math.random());
			}
			dividers[contCount] = junkHeight;
		}
		
		BufferedImage bmp = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
		int offsetY = 0;
		
		// 정크 영역 랜덤 채우기
		offsetY += setJunkRGB(bmp, offsetY, width, dividers[0]);

		for (int i = 0; i < contCount; i++) {
			// 컨테이너 데이터 쓰기
			Container cont = containers.get(i);
			int[] rgbs = cont.toRGBs(width);
			int pixelCount = cont.getRGBPixelCount();
			int contHeight = (pixelCount + width - 1) / width;
			logger.info(pad(pixelCount, 6) + " → " + pad(rgbs.length, 7) + "(" + width + " x " + pad(contHeight, 3) + "): " + cont.path);
			// 끄트머리 남는 공간 랜덤으로 채우기
			for (int j = pixelCount; j < width * contHeight; j++) {
				rgbs[j] = (int) (Math.random() * Integer.MAX_VALUE);
			}
			bmp.setRGB(0, offsetY, width, contHeight, rgbs, 0, width);
			offsetY += contHeight;

			// 정크 영역 랜덤 채우기
			offsetY += setJunkRGB(bmp, offsetY, width, dividers[i+1] - dividers[i]);
		}
		
		return bmp;
	}
	/**
	 * 정크 영역 랜덤 채우기
	 * @param bmp
	 * @param offsetY
	 * @param width
	 * @param height
	 * @return
	 */
	private static int setJunkRGB(BufferedImage bmp, int offsetY, int width, int height) {
		int pixelCount = width * height;
		if (height == 0) {
			return 0;
		}
		logger.info(pad(pixelCount, 6) + " → " + pad(pixelCount, 7) + "(" + width + " x " + pad(height, 3) + "): JUNK DATA");
		int[] rgbs = new int[pixelCount];
		rgbs[0] = 0; // pathLength
		rgbs[1] = (pixelCount - 2) * 3; // binaryLength
		for (int i = 2; i < pixelCount; i++) {
			rgbs[i] = (int) (Math.random() * Integer.MAX_VALUE);
		}
		bmp.setRGB(0, offsetY, width, height, rgbs, 0, width);
		return height;
	}

	/**
	 * 컨테이너 목록을 이미지로 이중 변환
	 * 한 번 무손실 압축이 됐기 때문에 재변환 시 비트맵 크기가 줄어듦
	 * @param containers: 컨테이너 목록
	 * @return: 비트맵 이미지
	 * @throws Exception
	 */
	public static BufferedImage toBitmapTwice(List<Container> containers) throws Exception {
		return toBitmapTwice(containers, 0);
	}
	/**
	 * 컨테이너 목록을 이미지로 이중 변환
	 * 한 번 무손실 압축이 됐기 때문에 재변환 시 비트맵 크기가 줄어듦
	 * @param containers: 컨테이너 목록
	 * @param minWidth: 최소 폭
	 * @return: 비트맵 이미지
	 * @throws Exception
	 */
	public static BufferedImage toBitmapTwice(List<Container> containers, int minWidth) throws Exception {
		return toBitmapTwice(containers, minWidth, true);
	}
	/**
	 * 컨테이너 목록을 이미지로 이중 변환
	 * 한 번 무손실 압축이 됐기 때문에 재변환 시 비트맵 크기가 줄어듦
	 * @param containers: 컨테이너 목록
	 * @param minWidth: 최소 폭
	 * @param twiceForced: 최소 폭보다 작더라도 난수화를 위해 이중 변환
	 * @return: 비트맵 이미지
	 * @throws Exception
	 */
	public static BufferedImage toBitmapTwice(List<Container> containers, int minWidth, boolean twiceForced) throws Exception {
		logger.info("\nContainer.toBitmapTwice");
		if (!twiceForced && ((8 * getRatio(containers)) < minWidth)) {
			logger.info("최소 폭보다 작을 경우 이중 변환할 필요 없음");
			return toBitmap(containers, minWidth);
		}
		
		File file = File.createTempFile("temp", ".png");
		file.deleteOnExit();
		ImageIO.write(toBitmap(containers), "PNG", file);
		Container cont = new Container("", file); // 파일명이 없는 컨테이너화
		
		List<Container> list = new ArrayList<>();
		list.add(cont);
		BufferedImage bmp2 = toBitmap(list, minWidth);
		
		return bmp2;
	}
	
	/**
	 * 비트맵 이미지를 컨테이너 목록으로 변환
	 * @param bmp
	 * @return 컨테이너 목록
	 * @throws Exception
	 */
	public static List<Container> fromBitmap(BufferedImage bmp) throws Exception {
		logger.info("\nContainer.fromBitmap: " + bmp);
		List<Container> containers = new ArrayList<Container>();
		
		int offsetY = 0;
		int width = bmp.getWidth();
		int height = bmp.getHeight();
		logger.info("input size: " + width + " x " + height);
		
		try {
			if (((bmp.getRGB(0, offsetY) >> 24) & 0xFF) == 0xFF) {
				// RGB
				while (offsetY < height) {
					int pathLength   = 0xFFFFFF & bmp.getRGB(0, offsetY);
					int binaryLength = 0xFFFFFF & bmp.getRGB(1, offsetY);
					int pixelCount = 2 + ((pathLength + 2) / 3) + ((binaryLength + 2) / 3);
					int contHeight = (pixelCount + width - 1) / width;
					logger.debug("");
					logger.debug("pathLength  : " + pathLength);
					logger.debug("binaryLength: " + binaryLength);
					logger.debug("pixelCount: " + pixelCount);
					logger.debug("contHeight: " + contHeight);
					
					int[] rgbs = bmp.getRGB(0, offsetY, width, contHeight, new int[width * contHeight], 0, width);
					Container cont = new Container(rgbs);
					logger.debug("path: " + cont.path);
					
					if (pathLength == 0) {
						// 경로가 없음: 이중 변환 or 정크 영역
						cont.path = "temp.png";
						File file = cont.toFile(System.getProperty("java.io.tmpdir"));
						file.deleteOnExit();
						try {
							// 이중 변환으로 가정하고 해석 시도
							containers.addAll(Container.fromBitmap(ImageIO.read(file)));
						} catch (Exception e) {
							logger.info("JUNK DATA");
							offsetY += contHeight;
							continue;
						}
					} else {
						// 경로가 있음: 일반 파일
						containers.add(cont);
					}
					
					offsetY += contHeight;
				}
			
			} else {
				// RGBA: 처음 개발했던 RGBA에 대해 레거시 지원
				logger.warn("개발 도중 레거시 형식 지원");
				while (offsetY < height) {
					int pathLength = bmp.getRGB(0, offsetY);
					int binaryLength = bmp.getRGB(1, offsetY);
					int pixelCount = 2 + ((pathLength + 3) / 4) + ((binaryLength + 3) / 4);
					int contHeight = (pixelCount + width - 1) / width;
					logger.debug("");
					logger.debug("pathLength  : " + pathLength);
					logger.debug("binaryLength: " + binaryLength);
					logger.debug("pixelCount: " + pixelCount);
					logger.debug("contHeight: " + contHeight);
					
					int[] argbs = bmp.getRGB(0, offsetY, width, contHeight, new int[width * contHeight], 0, width);
					Container cont = new Container(argbs);
					logger.debug("path: " + cont.path);
					
					if (pathLength == 0) {
						// 경로가 없음: 이중 변환
						cont.path = "temp.png";
						File file = cont.toFile(System.getProperty("java.io.tmpdir"));
						file.deleteOnExit();
						bmp = ImageIO.read(file);
						containers.addAll(fromBitmap(bmp));
					} else {
						// 경로가 있음: 일반 파일
						containers.add(cont);
					}
					
					offsetY += contHeight;
				}
			}
		} catch (Exception e) {
			logger.info("이미지 해석 실패");
			logger.debug(e);
		}
		
		return containers;
	}
	
	/**
	 * 이미지+컨테이너
	 * 
	 * @author harne_
	 * 
	 * ────────────────────────────────
	 * 
	 * 이미지+컨테이너 → 이미지 변환 방식 
	 * 
	 * a: resizedTargetImage
	 * d: dataImage
	 * b: (a+d)/₂
	 * c: (1+a-d)/₂
	 * 
	 *  (2*x  ,2*y  )  │  (2*x+1,2*y  )
	 *        a        │   b = (a+d)/₂
	 * ────────┼───────
	 *  (2*x  ,2*y+1)  │  (2*x+1,2*y+1)
	 *  c = (1+a-d)/₂  │        a
	 * 
	 * 4픽셀 합계: 3a + ½ -> 평균 내면 a 값에 따라 ⅛~⅞ 값을 갖게 됨
	 * 
	 * ────────────────────────────────
	 * 
	 * 이미지 → 이미지+컨테이너 변환 방식 
	 * 
	 * {1/2 + b - c} = {1/2 + (a+d)/2 - (1+a-d)/2} = {(1+a+d-1-a+d)/2} = d
	 * checksum = {½ + a - b - c} = {½ + 2a/₂ - (a+d)/₂ - (1+a-d)/₂}
	 *          = {(1 + 2a -(a+d)-(1+a-d))/₂}
	 *          = {(1 + 2a - a-d - 1-a+d )/₂} = 0
	 * ※ 체크섬은 정수연산 한계로 a+d가 홀수일 땐 1이 나옴
	 * 
	 * ────────────────────────────────
	 * 
	 * 진리표 (0~255 대신 0~7 기준 예시)
	 * 
	 * a,d에서 -> b,c 생성
	 * 
	 * ｂ　│ａ              　│　ｃ　│ａ       
	 * 　　│０１２３４５６７　│　　　│０１２３４５６７
	 * ──┼────────　│　──┼────────
	 * ｄ０│００１１２２３３　│　ｄ０│４４５５６６７７
	 * 　１│０１１２２３３４　│　　１│３４４５５６６７
	 * 　２│１１２２３３４４　│　　２│３３４４５５６６
	 * 　３│１２２３３４４５　│　　３│２３３４４５５６
	 * 　４│２２３３４４５５　│　　４│２２３３４４５５
	 * 　５│２３３４４５５６　│　　５│１２２３３４４５
	 * 　６│３３４４５５６６　│　　６│１１２２３３４４
	 * 　７│３４４５５６６７　│　　７│０１１２２３３４
	 * 
	 * b&c에서 d 복원 및 a 범위
	 * 
	 * ｄ　│ｂ　　　　　　　　│　ａ　│ｂ                                   
	 * 　　│０１２３４５６７　│　　　│０ １ ２ ３ ４ ５ ６ ７                     
	 * ──┼────────　│　──┼─-─-─-─-─-─-─-─                     
	 * ｃ０│－－－７－－－－　│　ｃ０│－ － － -0 － － － －                    
	 * 　１│－－５６７－－－　│　　１│－ － -0 01 12 － － －                  
	 * 　２│－３４５６７－－　│　　２│－ -0 01 12 23 34 － －                
	 * 　３│１２３４５６７－　│　　３│-0 01 12 23 34 45 56 －              
	 * 　４│０１２３４５６７　│　　４│01 12 23 34 45 56 67 7-             
	 * 　５│－０１２３４５－　│　　５│－ 23 34 45 56 67 7- －               
	 * 　６│－－０１２３－－　│　　６│－ － 45 56 67 7- － －                 
	 * 　７│－－－０１－－－　│　　７│－ － － 67 7- － － － 
	 * 
	 * ──────────────────────────────── 
	 *
	 */
	public static class WithTarget {
		public BufferedImage targetImage; // 출력물을 꾸며줄 이미지
		public List<Container> containers; // 컨테이너 목록

		public WithTarget(BufferedImage targetImage, List<Container> containers) {
			this.targetImage = targetImage;
			this.containers = containers;
		}

		/**
		 * 이미지+컨테이너를 이미지로 변환 개발 도중 레거시 형식
		 * 
		 * @param targetImage: 원하는 출력 이미지
		 * @param containers: 컨테이너 목록
		 * @param minWidth: 최소 폭
		 * @return 비트맵 이미지
		 * @throws Exception
		 */
		@Deprecated
		public BufferedImage toBitmapLegacy() throws Exception {
			return toBitmapLegacy(0);
		}
		/**
		 * 이미지+컨테이너를 이미지로 변환 개발 도중 레거시 형식
		 * 
		 * @param targetImage: 원하는 출력 이미지
		 * @param containers: 컨테이너 목록
		 * @return 비트맵 이미지
		 * @throws Exception
		 */
		@Deprecated
		public BufferedImage toBitmapLegacy(int minWidth) throws Exception {
			logger.warn("\nWithTarget.toBitmapPrototype - 개발 도중 레거시 형식");
			BufferedImage dataImage = toBitmapTwice(containers, minWidth / 2);
			int w = dataImage.getWidth(), h = dataImage.getHeight();
			
			// 이미지를 데이터와 같은 크기로 조절
			BufferedImage resizedTargetImage = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
			Graphics graphics = resizedTargetImage.getGraphics();
			graphics.drawImage(targetImage.getScaledInstance(w, h, BufferedImage.TYPE_3BYTE_BGR), 0, 0, null);
			graphics.dispose();
			
			/*
			 * rrggbb: resizedTargetImage
			 * RRGGBB: dataImage
			 * 
			 *  (2*x  ,2*y  )  │  (2*x+1,2*y  )
			 *     rrggbb      │     RRGGBB
			 * ────────┼───────
			 *  (2*x  ,2*y+1)  │  (2*x+1,2*y+1)
			 * 0xFFFFFF-RRGGBB │     rrggbb
			 * 
			 * 4픽셀 합계: 2 * rrggbb + 0xFFFFFF -> 평균 내면 rrggbb 값에 따라 ¼~¾ 값을 갖게 됨
			 */
			
			BufferedImage result = new BufferedImage(w*2, h*2, BufferedImage.TYPE_3BYTE_BGR);
			
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					result.setRGB(2*x  , 2*y  , resizedTargetImage.getRGB(x, y));
					result.setRGB(2*x+1, 2*y+1, resizedTargetImage.getRGB(x, y));
					result.setRGB(2*x+1, 2*y  , dataImage.getRGB(x, y));
					result.setRGB(2*x  , 2*y+1, 0xFFFFFF - dataImage.getRGB(x, y));
				}
			}
			
			return result;
		}
		/**
		 * 이미지+컨테이너를 이미지로 변환
		 * @return 이미지
		 * @throws Exception
		 */
		public BufferedImage toBitmap() throws Exception {
			return toBitmap(0);
		}
		/**
		 * 이미지+컨테이너를 이미지로 변환
		 * 
		 * @return 비트맵 이미지
		 * @throws Exception
		 */
		public BufferedImage toBitmap(int minWidth) throws Exception {
			logger.info("\nWithTarget.toBitmap");
			BufferedImage dataImage = toBitmapTwice(containers, minWidth / 2);
			int w = dataImage.getWidth(), h = dataImage.getHeight();
			
			// 이미지를 데이터와 같은 크기로 조절
			BufferedImage resizedTargetImage = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
			Graphics graphics = resizedTargetImage.getGraphics();
			graphics.drawImage(targetImage.getScaledInstance(w, h, BufferedImage.TYPE_3BYTE_BGR), 0, 0, null);
			graphics.dispose();
			
			/*
			 * a: resizedTargetImage
			 * d: dataImage
			 * 
			 *  (2*x  ,2*y  )  │  (2*x+1,2*y  )
			 *        a        │    (a+d)/₂
			 * ────────┼───────
			 *  (2*x  ,2*y+1)  │  (2*x+1,2*y+1)
			 *    (1+a-d)/₂    │        a
			 * 
			 * 4픽셀 합계: 3a + ½ -> 평균 내면 a 값에 따라 ⅛~⅞ 값을 갖게 됨
			 */
			
			BufferedImage result = new BufferedImage(w*2, h*2, BufferedImage.TYPE_3BYTE_BGR);
			int a, d;
			
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					a = 0xFFFFFF & resizedTargetImage.getRGB(x, y);
					d = 0xFFFFFF & dataImage.getRGB(x, y);
					
					result.setRGB(2*x  , 2*y  , a);
					result.setRGB(2*x+1, 2*y  , ( (((a&0xFF0000) + (d&0xFF0000)) & 0x1FE0000)
					                            | (((a&0x00FF00) + (d&0x00FF00)) & 0x001FE00)
					                            | (((a&0x0000FF) + (d&0x0000FF)) & 0x00001FE)) >> 1);
					result.setRGB(2*x  , 2*y+1, ( ((0x1000000 + (a&0xFF0000) - (d&0xFF0000)) & 0x1FE0000)
					                            | ((0x0010000 + (a&0x00FF00) - (d&0x00FF00)) & 0x001FE00)
					                            | ((0x0000100 + (a&0x0000FF) - (d&0x0000FF)) & 0x00001FE)) >> 1);
					result.setRGB(2*x+1, 2*y+1, a);
				}
			}
			
			return result;
		}
		
		/**
		 * 비트맵 이미지를 꾸며준 이미지와 컨테이너로 분리
		 * @param bmp
		 * @return
		 * @throws Exception
		 */
		@Deprecated
		public static WithTarget fromBitmapPrototype(BufferedImage bmp, boolean tryWithout) throws Exception {
			logger.warn("\nWithTarget.fromBitmapPrototype - 개발 도중 레거시 형식 지원");
			
			int width = bmp.getWidth();
			int height = bmp.getHeight();
			logger.info("input size: " + width + " x " + height);
			
			do {
				try {
					// 결합 이미지일 경우 크기는 짝수여야 함
					if (width % 2 > 0 || height % 2 > 0) break;
					
					// (2x,2y)와 (2x+1,2y+1)은 둘 다 출력물 이미지의 원본 픽셀로 같은 값이어야 함
					if (bmp.getRGB(0, 0) != bmp.getRGB(1, 1)) break;
					
					// (2x+1,2y)와 (2x,2y+1)은 합쳐서 0xFFFFFF가 나와야 함
					if ((bmp.getRGB(0, 1)&0xFFFFFF) + (bmp.getRGB(1, 0)&0xFFFFFF) != 0xFFFFFF) break;
					
					BufferedImage targetImage = new BufferedImage(width / 2, height / 2, BufferedImage.TYPE_3BYTE_BGR);
					BufferedImage dataImage   = new BufferedImage(width / 2, height / 2, BufferedImage.TYPE_3BYTE_BGR);
					
					for (int y = 0; y < height / 2; y++) {
						for (int x = 0; x < width / 2; x++) {
							targetImage.setRGB(x, y, bmp.getRGB(2*x  , 2*y));
							dataImage  .setRGB(x, y, bmp.getRGB(2*x+1, 2*y));
						}
					}
					List<Container> containers = Container.fromBitmap(dataImage);
					if (containers.size() > 0) {
						return new WithTarget(targetImage, containers);
					}
					
				} catch (Exception e) {
					logger.info("이미지 해석 실패");
					logger.debug(e);
				}
			} while (false);
			
			// 위에서 실패했을 경우 without target 진행
			if (tryWithout) {
				logger.info("Without target");
				List<Container> containers = Container.fromBitmap(bmp);
				if (containers.size() > 0) {
					return new WithTarget(null, containers);
				}
			}
			
			return null;
		}
		public static WithTarget fromBitmap(BufferedImage bmp) throws Exception {
			logger.info("\nWithTarget.fromBitmap");
			
			int width = bmp.getWidth();
			int height = bmp.getHeight();
			logger.info("input size: " + width + " x " + height);
			
			do {
				try {
					// 결합 이미지일 경우 크기는 짝수여야 함
					if (width % 2 > 0 || height % 2 > 0) break;
					
					int a, b, c, d;
					
					{	// 몇 블럭만 샘플로 검증해보고 진행
						boolean checkFailed = false;
						int checksum, compare;
						
						for (int i = 0; i < 10; i++) {
							int x = (int) (Math.random() * width ) / 2;
							int y = (int) (Math.random() * height) / 2;
							
							// (2x,2y)와 (2x+1,2y+1)은 둘 다 출력물 이미지의 원본 픽셀로 같은 값이어야 함
							a = bmp.getRGB(2*x  , 2*y  ) & 0xFFFFFF;
							d = bmp.getRGB(2*x+1, 2*y+1) & 0xFFFFFF;
							if (a != d) {
								logger.info("a != d");
								checkFailed = true;
								break;
							}
							
							// 패리티 검증
							b = bmp.getRGB(2*x+1, 2*y  ) & 0xFFFFFF; // (a+d)/₂
							c = bmp.getRGB(2*x  , 2*y+1) & 0xFFFFFF; // (1+a-d)/₂
							// {½ + b - c} = {½ + (a+d)/₂ - (1+a-d)/₂} = {(1+a+d-1-a+d)/₂} = d
							d = (0x800000 + (b&0xFF0000) - (c&0xFF0000))
							  | (0x008000 + (b&0x00FF00) - (c&0x00FF00))
							  | (0x000080 + (b&0x0000FF) - (c&0x0000FF));
							
							// {½ + a - b - c} = {½ + 2a/₂ - (a+d)/₂ - (1+a-d)/₂} = {(1+2a-a-d-1-a+d)/₂} = 0
							checksum = (0x800000 + (a&0xFF0000) - (b&0xFF0000) - (c&0xFF0000))
							         + (0x008000 + (a&0x00FF00) - (b&0x00FF00) - (c&0x00FF00))
							         + (0x000080 + (a&0x0000FF) - (b&0x0000FF) - (c&0x0000FF));
							
							logger.debug("sample(" + pad(x, 4) + ", " + pad(y, 4) + "):"
									+ " { " + pad(Integer.toHexString(a), 6)
									+ " / " + pad(Integer.toHexString(b), 6)
									+ " / " + pad(Integer.toHexString(c), 6)
									+ " / " + pad(Integer.toHexString(d), 6)
									+ " } -> checksum: " + pad(Integer.toHexString(checksum), 6));
							
							compare = (((a&0x010101)+(d&0x010101))&0x010101); // 정수연산 한계로 a+d가 홀수일 땐 1이 나옴
							if (checksum != compare) {
								logger.debug("is not " + pad(Integer.toHexString(compare), 6));
								checkFailed = true;
								break;
							}
						}
						if (checkFailed) {
							logger.info("체크섬 오류 - 현행 WithTarget 형식 이미지가 아님");
							break;
						}
					}
					
					BufferedImage targetImage = new BufferedImage(width / 2, height / 2, BufferedImage.TYPE_3BYTE_BGR);
					BufferedImage dataImage   = new BufferedImage(width / 2, height / 2, BufferedImage.TYPE_3BYTE_BGR);
					
					for (int y = 0; y < height / 2; y++) {
						for (int x = 0; x < width / 2; x++) {
							a = bmp.getRGB(2*x  , 2*y  ) & 0xFFFFFF;
							b = bmp.getRGB(2*x+1, 2*y  ) & 0xFFFFFF; // (a+d)/2
							c = bmp.getRGB(2*x  , 2*y+1) & 0xFFFFFF; // (1+a-d)/2
							// {1/2 + b - c} = {1/2 + (a+d)/2 - (1+a-d)/2} = {(1+a+d-1-a+d)/2} = d
							d = (0x800000 + (b&0xFF0000) - (c&0xFF0000))
							  | (0x008000 + (b&0x00FF00) - (c&0x00FF00))
							  | (0x000080 + (b&0x0000FF) - (c&0x0000FF));
							targetImage.setRGB(x, y, a);
							dataImage  .setRGB(x, y, d);
						}
					}
					List<Container> containers = Container.fromBitmap(dataImage);
					if (containers.size() > 0) {
						return new WithTarget(targetImage, containers);
					}
					
				} catch (Exception e) {
					logger.info("이미지 해석 실패");
					logger.debug(e);
				}
			} while (false);
			
			logger.info("Without target 해석 시도");
			List<Container> containers = Container.fromBitmap(bmp);
			if (containers.size() > 0) {
				return new WithTarget(null, containers);
			}
			
			logger.info("현행 방식으로 해석 실패했을 경우, 개발 도중 레거시 형식으로 재시도");
			return fromBitmapPrototype(bmp, false);
		}
	}
}