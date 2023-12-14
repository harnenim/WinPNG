package moe.ohli.pngb;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
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
	
	private static String pad(Object str, int len) {
		return pad(str.toString(), len, ' ');
	}
	private static String pad0(String str, int len) {
		return pad(str, len, '0');
	}
	private static String pad(String str, int len, char pad) {
		while (str.length() < len) {
			str = pad + str;
		}
		return str;
	}
	private static final double RATIO = 9 / 16.0;
	
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
		this(rgbs, 0, new int[0]);
	}
	/**
	 * 이미지 픽셀 RGB 값을 주어진 값에 따라 변환하여 생성
	 * @param rgbs
	 * @param shift
	 * @param xors
	 * @throws Exception
	 */
	public Container(int[] rgbs, int shift, int[] xors) throws Exception {
		logger.debug("pathLength  : " + pad0(Integer.toHexString(rgbs[ shift    % rgbs.length]), 8));
		logger.debug("binaryLength: " + pad0(Integer.toHexString(rgbs[(shift+1) % rgbs.length]), 8));
		// xor 연산 수행
		if (xors.length > 0) {
			logger.debug("xors0       : " + pad0(Integer.toHexString(xors[ shift    % xors.length]), 8));
			logger.debug("xors1       : " + pad0(Integer.toHexString(xors[(shift+1) % xors.length]), 8));
			for (int i = 0; i < rgbs.length; i++) {
				rgbs[i] ^= xors[i % xors.length];
			}
		}
		int pathLength   = rgbs[ shift    % rgbs.length] & 0xFFFFFF;
		int binaryLength = rgbs[(shift+1) % rgbs.length] & 0xFFFFFF;
		logger.debug("pathLength  : " + pathLength);
		logger.debug("binaryLength: " + binaryLength);
		
		setDataFromRGBs(rgbs, shift, pathLength, binaryLength);
	}
	/**
	 * 이미지 픽셀 RGB 값에서 생성
	 * xor 연산 수행 이후 동작
	 * @param rgbs
	 * @throws Exception
	 */
	public Container(int[] rgbs, int shift, int pathLength, int binaryLength) throws Exception {
		setDataFromRGBs(rgbs, shift, pathLength, binaryLength);
	}
	private void setDataFromRGBs(int[] rgbs, int shift, int pathLength, int binaryLength) throws Exception {
		try {
			if (binaryLength > 20971520) {
				throw new Exception("이미지 해석 오류");
			}
			
			int offset = shift + 2;
			
			if (((rgbs[shift] >> 24) & 0xFF) == 0xFF) {
				// RGB
				logger.debug("RGB");
				byte[] bytes = new byte[pathLength];
				for (int i = 0; i < pathLength; i++) {
					bytes[i] = (byte) ((rgbs[(offset + (i/3)) % rgbs.length] >> (8 * (2-(i%3)))) & 0xFF);
				}
				logger.debug("pathBytes: " + bytes.length);
				this.path = new String(bytes, "UTF-8");
				offset += (pathLength + 2) / 3;
				
				this.binary = new byte[binaryLength];
				for (int i = 0; i < binaryLength; i++) {
					this.binary[i] = (byte) ((rgbs[(offset + (i/3)) % rgbs.length] >> (8 * (2-(i%3)))) & 0xFF);
				}
			} else {
				// RGBA: 처음 개발했던 RGBA에 대해 레거시 지원 - shift 쓴 적 없음
				logger.debug("RGBA");
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
	 * @param shift: 출력물 픽셀 밀기
	 * @param xors: 출력물 xor 연산 수행
	 * @return
	 */
	private int[] toRGBs(int width, int shift, int[] xors, boolean randomJunk) {
		int contPixelCount = getRGBPixelCount();
		int contHeight = (contPixelCount + width - 1) / width;
		int rectPixelCount = width * contHeight;
		
		byte[] pathBytes = getPathBytes();
		int[] rgbs = new int[rectPixelCount];
		int offset = (shift = shift % width);
		
		// 경로 길이 1바이트
		rgbs[offset++ % rectPixelCount] = pathBytes.length;
		// 데이터 길이 1바이트
		rgbs[offset++ % rectPixelCount] = binary.length;
		
		// 경로
		int i = 0;
		for (; i < pathBytes.length / 3; i++) {
			rgbs[offset++ % rectPixelCount] = threeBytesToInt(pathBytes, i * 3);
		}
		if (pathBytes.length % 3 > 0) {
			byte[] last = new byte[] { pathBytes[i*3], 0, 0 };
			if (pathBytes.length % 3 > 1) {
				last[1] = pathBytes[i*3+1];
			}
			rgbs[offset++ % rectPixelCount] = threeBytesToInt(last, 0);
		}
		
		// 데이터
		i = 0;
		for (; i < binary.length / 3; i++) {
			rgbs[offset++ % rectPixelCount] = threeBytesToInt(binary, i * 3);
		}
		if (binary.length % 3 > 0) {
			byte[] last = new byte[] { binary[i*3], 0, 0 };
			if (binary.length % 3 > 1) {
				last[1] = binary[i*3+1];
			}
			rgbs[offset++ % rectPixelCount] = threeBytesToInt(last, 0);
		}
		
		// 직사각형 끄트머리 남는 공간 랜덤으로 채우기
		if (randomJunk) {
			while (offset < rectPixelCount + shift) {
				rgbs[offset++ % rectPixelCount] = (int) (Math.random() * Integer.MAX_VALUE);
			}
		}
		
		// xor 연산 수행
		if (xors.length > 0) {
			for (i = 0; i < rectPixelCount; i++) {
				rgbs[i] ^= xors[i % xors.length];
			}
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
	 * 3바이트씩 RGB int 배열로 변환
	 * @param bytes
	 * @return
	 */
	private static int[] bytesToRGBs(byte[] bytes) {
		int[] result = new int[(bytes.length + 2) / 3];
		for (int i = 0; i < result.length; i++) {
			result[i] = 0;
		}
		for (int i = 0; i < bytes.length; i++) {
			result[i / 3] |= ((((int) bytes[i]) & 0xFF) << ((2 - (i % 3)) * 8)) & 0xFFFFFF;
		}
		return result;
	}
	/**
	 * 비밀번호 키에서 암호화 값 구하기
	 * @param key
	 * @return
	 */
	private static final int getShift(String key) {
		return fibonacci(key.length());
	}
	private static final int[] getXors(String key) {
		try {
			byte[] bytes = key.getBytes("UTF-8");
			for (int i = 0; i < bytes.length; i++) {
				bytes[i] = (byte)               // 75361420
						( ((bytes[i] << (7-0)) & 0b10000000)
						| ((bytes[i] << (5-1)) & 0b01000000)
						| ((bytes[i] << (3-2)) & 0b00100000)
						| ((bytes[i] << (6-3)) & 0b00010000)
						| ((bytes[i] << (1-4)) & 0b00001000)
						| ((bytes[i] << (4-5)) & 0b00000100)
						| ((bytes[i] << (2-6)) & 0b00000010)
						| ((bytes[i] << (0-7)) & 0b00000001)
						);
			}
			return bytesToRGBs(bytes);
			
		} catch (UnsupportedEncodingException e) {
			logger.error(e);
			return new int[0];
		}
	}
	private static int fibonacci(int no) {
		return fibonacci(0, 1, no - 1);
	}
	private static int fibonacci(int before, int current, int left) {
		if (left < 0) {
			return before;
		} else if (left == 0) {
			return current;
		} else {
			return fibonacci(current, before + current, left - 1);
		}
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
	 * @return 컨테이너 목록
	 * @throws Exception
	 */
	public static List<Container> fileToContainers(File file) throws Exception {
		List<Container> containers = new ArrayList<>();
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
		List<File> files = new ArrayList<>();
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
	 * @param ratio 가로-세로 비율
	 * @return 좌우 폭
	 */
	private static int getWidthByRatio(List<Container> containers, double ratio) throws Exception {
		// 필요한 크기 계산(부정확함)
		int size = 0;
		for (Container cont : containers) {
			size += cont.getRGBPixelCount();
		}
		if (size > 20971520) {
			throw new Exception("20MB를 초과합니다");
		}
		
		return (int) Math.round(Math.sqrt(size / ratio));
	}
	/**
	 * 컨테이너 목록을 이미지로 변환
	 * @param containers: 컨테이너 목록
	 * @return: 비트맵 이미지
	 * @throws Exception
	 */
	public static BufferedImage toBitmap(List<Container> containers) throws Exception {
		return toBitmap(containers, true);
	}
	private static BufferedImage toBitmap(List<Container> containers, boolean randomJunk) throws Exception {
		return toBitmap(containers, 0, RATIO, 1, 0, new int[0], randomJunk);
	}
	/**
	 * 컨테이너 목록을 최소 폭과 비율에 맞춘 크기의 이미지로 변환
	 * @param containers: 컨테이너 목록
	 * @param minWidth: 최소 폭
	 * @param ratio: 가로/세로 비율
	 * @return: 비트맵 이미지
	 * @throws Exception
	 */
	public static BufferedImage toBitmap(List<Container> containers, int minWidth, double ratio) throws Exception {
		return toBitmap(containers, minWidth, ratio, true);
	}
	private static BufferedImage toBitmap(List<Container> containers, int minWidth, double ratio, boolean randomJunk) throws Exception {
		return toBitmap(containers, minWidth, ratio, 1, 0, new int[0], randomJunk);
	}
	/**
	 * 컨테이너 목록을 최소 폭과 비율에 맞춘 크기의 이미지로 변환
	 * @param containers: 컨테이너 목록
	 * @param minWidth: 최소 폭
	 * @param ratio: 가로/세로 비율
	 * @param key
	 * @return: 비트맵 이미지
	 * @throws Exception
	 */
	public static BufferedImage toBitmap(List<Container> containers, int minWidth, double ratio, String key) throws Exception {
		return toBitmap(containers, minWidth, ratio, key, true);
	}
	private static BufferedImage toBitmap(List<Container> containers, int minWidth, double ratio, String key, boolean randomJunk) throws Exception {
		return toBitmap(containers, minWidth, ratio, 1, key, randomJunk);
	}
	/**
	 * 컨테이너 목록을 최소 폭과 비율에 맞춘 크기의 이미지로 변환
	 * @param containers: 컨테이너 목록
	 * @param minWidth: 최소 폭
	 * @param ratio: 가로/세로 비율
	 * @param unit: 크기 단위
	 * @param key
	 * @return
	 * @throws Exception
	 */
	public static BufferedImage toBitmap(List<Container> containers, int minWidth, double ratio, int unit, String key) throws Exception {
		return toBitmap(containers, minWidth, ratio, key, true);
	}
	private static BufferedImage toBitmap(List<Container> containers, int minWidth, double ratio, int unit, String key, boolean randomJunk) throws Exception {
		return toBitmap(containers, minWidth, ratio, unit, getShift(key), getXors(key), randomJunk);
	}
	/**
	 * 컨테이너 목록을 최소 폭과 비율에 맞춘 크기의 이미지로 변환
	 * @param containers: 컨테이너 목록
	 * @param minWidth: 최소 폭
	 * @param ratio: 가로/세로 비율
	 * @param unit: 크기 단위
	 * @param shift: 출력물 픽셀 밀기
	 * @param xors: 출력물 xor 연산 수행
	 * @return: 비트맵 이미지
	 * @throws Exception
	 */
	public static BufferedImage toBitmap(List<Container> containers, int minWidth, double ratio, int unit, int shift, int[] xors) throws Exception {
		return toBitmap(containers, minWidth, ratio, unit, shift, xors, true);
	}
	private static BufferedImage toBitmap(List<Container> containers, int minWidth, double ratio, int unit, int shift, int[] xors, boolean randomJunk) throws Exception {
		logger.info("\nContainer.toBitmap");
		// 최종 이미지 크기 구하기
		int contCount = containers.size();
		
		// 간이로 구한 폭
		int width = (Math.max(minWidth, getWidthByRatio(containers, ratio)) + unit - 1) / unit * unit;
		int containersHeight = 0;
		for (Container cont : containers) {
			containersHeight += (cont.getRGBPixelCount() + width - 1) / width;
		}
		// 간이로 구한 높이 - 실제 높이는 각 컨테이너별 정크 영역 때문에 커지게 됨
		int height = Math.max((int) Math.round(width * ratio / unit) * unit, (containersHeight + unit - 1) / unit * unit);
		logger.info("check output size: " + width + " x " + height + " = " + (width * height));
		
		// 최대한 목표 비율에 가가운 값 찾기
		// 폭에 따라 정크 영역이 줄어들 수 있음
		double lastRatio = (double) height / width;
		for (int width0 = width; lastRatio > ratio; width0 += unit) {
			int width1 = width0 + unit;
			int containersHeight1 = 0;
			for (Container cont : containers) {
				containersHeight1 += (cont.getRGBPixelCount() + width1 - 1) / width1;
			}
			int height1 = Math.max((int) (width1 * ratio / unit) * unit, (containersHeight1 + unit - 1) / unit * unit);
			logger.info("check output size: " + width1 + " x " + height1 + " = " + (width1 * height1));
			double ratio1 = (double) height1 / width1;
			
			// 높이가 커졌으면 이미 타이트한 상태
			if (height1 > height) {
				break;
			}
			
			// 면적이 작아졌으면 해당 값 선택
			if (width1 * height1 < width * height) {
				width = width1;
				height = height1;
				containersHeight = containersHeight1;
			}
			lastRatio = ratio1;
		}
		logger.info("final output size: " + width + " x " + height);
		
		// 정크 영역 랜덤 배분
		// 위에서 높이를 타이트하게 구해서 사실상 배분이 이뤄질 게 없음...
		int[] dividers = new int[contCount + 1];
		{
			int junkHeight = height - containersHeight;
			logger.info("junkHeight: " + junkHeight);
			if (randomJunk) {
				for (int i = 0; i < contCount; i++) {
					dividers[i] = (int) (junkHeight * Math.random());
				}
			} else {
				for (int i = 0; i < contCount; i++) {
					dividers[i] = 0;
				}
			}
			dividers[contCount] = junkHeight;
		}
		
		BufferedImage bmp = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
		int offsetY = 0;
		
		// 정크 영역 랜덤 채우기
		offsetY += setJunkRGB(bmp, shift, xors, offsetY, width, dividers[0], randomJunk);
		
		for (int i = 0; i < contCount; i++) {
			// 컨테이너 데이터 쓰기
			Container cont = containers.get(i);
			int[] rgbs = cont.toRGBs(width, shift, xors, randomJunk);
			int contPixelCount = cont.getRGBPixelCount();
			int contHeight = rgbs.length / width;
			logger.info(pad(contPixelCount, 6) + " → " + pad(rgbs.length, 7) + "(" + width + " x " + pad(contHeight, 3) + "): " + cont.path);
			bmp.setRGB(0, offsetY, width, contHeight, rgbs, 0, width);
			offsetY += contHeight;

			// 정크 영역 랜덤 채우기
			offsetY += setJunkRGB(bmp, shift, xors, offsetY, width, dividers[i+1] - dividers[i], randomJunk);
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
	private static int setJunkRGB(BufferedImage bmp, int shift, int[] xors, int offsetY, int width, int height, boolean randomJunk) {
		int pixelCount = width * height;
		if (height == 0) {
			return 0;
		}
		logger.info(pad(pixelCount, 6) + " → " + pad(pixelCount, 7) + "(" + width + " x " + pad(height, 3) + "): JUNK DATA");
		bmp.setRGB(0, offsetY, width, height, new Container((pixelCount - 2) * 3, randomJunk).toRGBs(width, shift, xors, randomJunk), 0, width);
		return height;
	}
	private Container(int length, boolean randomJunk) {
		path = "";
		binary = new byte[length];
		if (randomJunk) {
			for (int i = 0; i < length; i++) {
				binary[i] = (byte) (Math.random() * 256);
			}
		}
	}
	
	/**
	 * 컨테이너 목록을 이미지로 이중 변환
	 * 한 번 무손실 압축이 됐기 때문에 재변환 시 비트맵 크기가 줄어듦 (용량 이득 X)
	 * @param containers: 컨테이너 목록
	 * @return: 비트맵 이미지
	 * @throws Exception
	 */
	public static BufferedImage toBitmapTwice(List<Container> containers) throws Exception {
		return toBitmapTwice(containers, 0);
	}
	/**
	 * 컨테이너 목록을 이미지로 이중 변환
	 * 한 번 무손실 압축이 됐기 때문에 재변환 시 비트맵 크기가 줄어듦 (용량 이득 X)
	 * @param containers: 컨테이너 목록
	 * @param key: 암호화 키
	 * @return: 비트맵 이미지
	 * @throws Exception
	 */
	public static BufferedImage toBitmapTwice(List<Container> containers, String key) throws Exception {
		return toBitmapTwice(containers, 0, key);
	}
	/**
	 * 컨테이너 목록을 이미지로 이중 변환
	 * 한 번 무손실 압축이 됐기 때문에 재변환 시 비트맵 크기가 줄어듦 (용량 이득 X)
	 * @param containers: 컨테이너 목록
	 * @param minWidth: 최소 폭
	 * @return: 비트맵 이미지
	 * @throws Exception
	 */
	public static BufferedImage toBitmapTwice(List<Container> containers, int minWidth) throws Exception {
		return toBitmapTwice(containers, minWidth, RATIO, 1);
	}
	/**
	 * 컨테이너 목록을 이미지로 이중 변환
	 * 한 번 무손실 압축이 됐기 때문에 재변환 시 비트맵 크기가 줄어듦 (용량 이득 X)
	 * @param containers: 컨테이너 목록
	 * @param minWidth: 최소 폭
	 * @param key: 암호화 키
	 * @return: 비트맵 이미지
	 * @throws Exception
	 */
	public static BufferedImage toBitmapTwice(List<Container> containers, int minWidth, String key) throws Exception {
		return toBitmapTwice(containers, minWidth, RATIO, 1, true, key);
	}
	/**
	 * 컨테이너 목록을 이미지로 이중 변환
	 * 한 번 무손실 압축이 됐기 때문에 재변환 시 비트맵 크기가 줄어듦 (용량 이득 X)
	 * @param containers: 컨테이너 목록
	 * @param minWidth: 최소 폭
	 * @param ratio: 가로/세로 비율
	 * @return: 비트맵 이미지
	 * @throws Exception
	 */
	public static BufferedImage toBitmapTwice(List<Container> containers, int minWidth, double ratio) throws Exception {
		return toBitmapTwice(containers, minWidth, ratio, 1);
	}
	/**
	 * 컨테이너 목록을 이미지로 이중 변환
	 * 한 번 무손실 압축이 됐기 때문에 재변환 시 비트맵 크기가 줄어듦 (용량 이득 X)
	 * @param containers: 컨테이너 목록
	 * @param minWidth: 최소 폭
	 * @param ratio: 가로/세로 비율
	 * @param key: 암호화 키
	 * @return: 비트맵 이미지
	 * @throws Exception
	 */
	public static BufferedImage toBitmapTwice(List<Container> containers, int minWidth, double ratio, String key) throws Exception {
		return toBitmapTwice(containers, minWidth, ratio, 1, true, key);
	}
	/**
	 * 컨테이너 목록을 이미지로 이중 변환
	 * 한 번 무손실 압축이 됐기 때문에 재변환 시 비트맵 크기가 줄어듦 (용량 이득 X)
	 * @param containers: 컨테이너 목록
	 * @param minWidth: 최소 폭
	 * @param ratio: 가로/세로 비율
	 * @param shift: 출력물 픽셀 밀기
	 * @param xors: 출력물 xor 연산 수행
	 * @return: 비트맵 이미지
	 * @throws Exception
	 */
	public static BufferedImage toBitmapTwice(List<Container> containers, int minWidth, double ratio, int shift, int[] xors) throws Exception {
		return toBitmapTwice(containers, minWidth, ratio, 1, true, shift, xors);
	}
	/**
	 * 컨테이너 목록을 이미지로 이중 변환
	 * 한 번 무손실 압축이 됐기 때문에 재변환 시 비트맵 크기가 줄어듦 (용량 이득 X)
	 * @param containers: 컨테이너 목록
	 * @param minWidth: 최소 폭
	 * @param ratio: 가로/세로 비율
	 * @param unit: 크기 단위
	 * @return: 비트맵 이미지
	 * @throws Exception
	 */
	public static BufferedImage toBitmapTwice(List<Container> containers, int minWidth, double ratio, int unit) throws Exception {
		return toBitmapTwice(containers, minWidth, ratio, unit, true, 0, new int[0]);
	}
	/**
	 * 컨테이너 목록을 이미지로 이중 변환
	 * 한 번 무손실 압축이 됐기 때문에 재변환 시 비트맵 크기가 줄어듦 (용량 이득 X)
	 * @param containers: 컨테이너 목록
	 * @param minWidth: 최소 폭
	 * @param ratio: 가로/세로 비율
	 * @param unit: 크기 단위
	 * @param key: 암호화 키
	 * @return: 비트맵 이미지
	 * @throws Exception
	 */
	public static BufferedImage toBitmapTwice(List<Container> containers, int minWidth, double ratio, int unit, String key) throws Exception {
		return toBitmapTwice(containers, minWidth, ratio, unit, true, key);
	}
	/**
	 * 컨테이너 목록을 이미지로 이중 변환
	 * 한 번 무손실 압축이 됐기 때문에 재변환 시 비트맵 크기가 줄어듦 (용량 이득 X)
	 * @param containers: 컨테이너 목록
	 * @param minWidth: 최소 폭
	 * @param ratio: 가로/세로 비율
	 * @param unit: 크기 단위
	 * @param shift: 출력물 픽셀 밀기
	 * @param xors: 출력물 xor 연산 수행
	 * @return: 비트맵 이미지
	 * @throws Exception
	 */
	public static BufferedImage toBitmapTwice(List<Container> containers, int minWidth, double ratio, int unit, int shift, int[] xors) throws Exception {
		return toBitmapTwice(containers, minWidth, ratio, unit, true, shift, xors);
	}
	/**
	 * 컨테이너 목록을 이미지로 이중 변환
	 * 한 번 무손실 압축이 됐기 때문에 재변환 시 비트맵 크기가 줄어듦 (용량 이득 X)
	 * @param containers: 컨테이너 목록
	 * @param minWidth: 최소 폭
	 * @param ratio: 가로/세로 비율
	 * @param unit: 크기 단위
	 * @param twiceForced: 최소 폭보다 작더라도 난수화를 위해 이중 변환
	 * @param key: 암호화 키
	 * @return: 비트맵 이미지
	 * @throws Exception
	 */
	public static BufferedImage toBitmapTwice(List<Container> containers, int minWidth, double ratio, int unit, boolean twiceForced, String key) throws Exception {
		return toBitmapTwice(containers, minWidth, ratio, unit, twiceForced, getShift(key), getXors(key));
	}
	/**
	 * 컨테이너 목록을 이미지로 이중 변환
	 * 한 번 무손실 압축이 됐기 때문에 재변환 시 비트맵 크기가 줄어듦 (용량 이득 X)
	 * @param containers: 컨테이너 목록
	 * @param minWidth: 최소 폭
	 * @param ratio: 가로/세로 비율
	 * @param unit: 크기 단위
	 * @param twiceForced: 최소 폭보다 작더라도 난수화를 위해 이중 변환
	 * @param shift: 출력물 픽셀 밀기
	 * @param xors: 출력물 xor 연산 수행
	 * @return: 비트맵 이미지
	 * @throws Exception
	 */
	public static BufferedImage toBitmapTwice(List<Container> containers, int minWidth, double ratio, int unit, boolean twiceForced, int shift, int[] xors) throws Exception {
		logger.info("\nContainer.toBitmapTwice");
		if (!twiceForced && ((getWidthByRatio(containers, ratio)) < minWidth)) {
			logger.info("최소 폭보다 작을 경우 이중 변환할 필요 없음");
			return toBitmap(containers, minWidth, ratio, unit, shift, xors);
		}
		
		File file = File.createTempFile("temp", ".png");
		file.deleteOnExit();
		// 1차 변환은 정크 영역 랜덤 없어야 용량 최적화
		// 비율은 일단 폭이 좁을수록 정크 영역이 줄어들 텐데...
		ImageIO.write(toBitmap(containers, 0, 8, false), "PNG", file);
		Container cont = new Container("", file); // 파일명이 없는 컨테이너화
		
		containers = new ArrayList<>();
		containers.add(cont);
		BufferedImage bmp2 = toBitmap(containers, minWidth, ratio, unit, shift, xors);
		
		return bmp2;
	}
	
	/**
	 * 비트맵 이미지를 컨테이너 목록으로 변환
	 * @param bmp
	 * @return 컨테이너 목록
	 * @throws Exception
	 */
	public static List<Container> fromBitmap(BufferedImage bmp) throws Exception {
		return fromBitmap(bmp, 0, new int[0]);
	}
	/**
	 * 비트맵 이미지를 컨테이너 목록으로 변환
	 * @param bmp
	 * @param key: 암호화 키
	 * @return 컨테이너 목록
	 * @throws Exception
	 */
	public static List<Container> fromBitmap(BufferedImage bmp, String key) throws Exception {
		return fromBitmap(bmp, getShift(key), getXors(key));
	}
	/**
	 * 비트맵 이미지를 컨테이너 목록으로 변환
	 * @param bmp
	 * @param shift: 출력물 픽셀 밀기
	 * @param xors: 출력물 xor 연산 수행
	 * @return 컨테이너 목록
	 * @throws Exception
	 */
	public static List<Container> fromBitmap(BufferedImage bmp, int shift, int[] xors) throws Exception {
		logger.info("\nContainer.fromBitmap: " + bmp);
		List<Container> containers = new ArrayList<>();
		
		int offsetY = 0;
		int width = bmp.getWidth();
		int height = bmp.getHeight();
		shift = shift % width;
		logger.info("input size: " + width + " x " + height);
		
		try {
			if (((bmp.getRGB(shift, offsetY) >> 24) & 0xFF) == 0xFF) {
				// RGB
				logger.debug("RGB");
				while (offsetY < height) {
					logger.debug("shift: " + shift);
					logger.debug("xors.length: " + xors.length);
					int pathLength   = 0xFFFFFF & bmp.getRGB( shift    % width, offsetY);
					int binaryLength = 0xFFFFFF & bmp.getRGB((shift+1) % width, offsetY);
					logger.debug("pathLength  : " + pad0(Integer.toHexString(pathLength  ), 8));
					logger.debug("binaryLength: " + pad0(Integer.toHexString(binaryLength), 8));
					if (xors.length > 0) {
						logger.debug("xors0       : " + pad0(Integer.toHexString(xors[ shift    % xors.length]), 8));
						logger.debug("xors1       : " + pad0(Integer.toHexString(xors[(shift+1) % xors.length]), 8));
						pathLength   = (pathLength   ^ xors[ shift    % xors.length]) & 0xFFFFFF;
						binaryLength = (binaryLength ^ xors[(shift+1) % xors.length]) & 0xFFFFFF;
					}
					int pixelCount = 2 + ((pathLength + 2) / 3) + ((binaryLength + 2) / 3);
					int contHeight = (pixelCount + width - 1) / width;
					logger.debug("");
					logger.debug("pathLength  : " + pathLength);
					logger.debug("binaryLength: " + binaryLength);
					logger.debug("pixelCount: " + pixelCount);
					logger.debug("contHeight: " + contHeight);
					
					int[] rgbs = bmp.getRGB(0, offsetY, width, contHeight, new int[width * contHeight], 0, width);
					Container cont = new Container(rgbs, shift, xors);
					logger.debug("path: " + cont.path);
					
					if (pathLength == 0) {
						// 경로가 없음: 이중 변환 or 정크 영역
						cont.path = "temp.png";
						File file = cont.toFile(System.getProperty("java.io.tmpdir"));
						file.deleteOnExit();
						try {
							// 이중 변환으로 가정하고 해석 시도
							containers.addAll(fromBitmap(ImageIO.read(file)));
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
				logger.debug("RGBA");
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
	 * 
	 * a,d에서 -> b,c 생성
	 * 
	 * (a+d)/2                     (8+a-d)/2
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
	 * ───────────
	 * b&c에서 d 복원 및 a 범위
	 * 
	 * 4 + b - c
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
	 * ───────────
	 * 패리티 연산
	 * 
	 * 4 + a - b - c
	 * ｆ　│ａ                     
	 * 　　│０１２３４５６７
	 * ──┼────────
	 * ｄ０│０１０１０１０１
	 * 　１│１０１０１０１０
	 * 　２│０１０１０１０１
	 * 　３│１０１０１０１０
	 * 　４│０１０１０１０１
	 * 　５│１０１０１０１０
	 * 　６│０１０１０１０１
	 * 　７│１０１０１０１０
	 *
	 */
	public static class WithTarget {
		public static final int TYPE_114 = 1;
		public static final int TYPE_149 = 2;
		public static final int TYPE_238 = 3;
		public static final int TYPE_429 = 4;
		
		public BufferedImage targetImage; // 출력물을 꾸며줄 이미지
		public List<Container> containers; // 컨테이너 목록
		public int type = TYPE_114;

		public WithTarget(BufferedImage targetImage, List<Container> containers) {
			this.targetImage = targetImage;
			this.containers = containers;
		}
		public WithTarget(BufferedImage targetImage, List<Container> containers, int type) {
			this.targetImage = targetImage;
			this.containers = containers;
			this.type = type;
		}
		
		private double getRatio() {
			return targetImage.getHeight() / (double) targetImage.getWidth();
		}

		/**
		 * 개발 도중 레거시 변환 형식 보존
		 * 
		 * @param targetImage: 원하는 출력 이미지
		 * @param containers: 컨테이너 목록
		 * @return 비트맵 이미지
		 * @throws Exception
		 */
		@Deprecated
		public BufferedImage toBitmapLegacy(int minWidth) throws Exception {
			logger.warn("\nWithTarget.toBitmapPrototype - 개발 도중 레거시 형식");
			BufferedImage dataImage = toBitmapTwice(containers, minWidth / 2, getRatio());
			int w = dataImage.getWidth(), h = dataImage.getHeight();
			
			// 이미지를 데이터와 같은 크기로 조절
			BufferedImage resizedTargetImage = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
			Graphics graphics = resizedTargetImage.getGraphics();
			graphics.drawImage(targetImage.getScaledInstance(w, h, Image.SCALE_SMOOTH), 0, 0, null);
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
		
		// b = (a+d)/₂
		private static int getB(int a, int d) {
			//     (                a           +  d                        ) / 2
			return ( ((            (a&0xFF0000) + (d&0xFF0000)) & 0x1FE0000)
			       | ((            (a&0x00FF00) + (d&0x00FF00)) & 0x001FE00)
			       | ((            (a&0x0000FF) + (d&0x0000FF)) & 0x00001FE)) >> 1;
		}
		// c = (1+a-d)/₂
		private static int getC(int a, int d) {
			//     (     1       +  a           +  d                        ) / 2
			return ( ((0x1000000 + (a&0xFF0000) - (d&0xFF0000)) & 0x1FE0000)
			       | ((0x0010000 + (a&0x00FF00) - (d&0x00FF00)) & 0x001FE00)
			       | ((0x0000100 + (a&0x0000FF) - (d&0x0000FF)) & 0x00001FE)) >> 1;
		}
		// {½ + b - c} = {½ + (a+d)/₂ - (1+a-d)/₂} = {(1+a+d-1-a+d)/₂} = d
		private static int getD(int b, int c) {
			//      1/2      +  b           -  c
			return (0x800000 + (b&0xFF0000) - (c&0xFF0000))
			     | (0x008000 + (b&0x00FF00) - (c&0x00FF00))
			     | (0x000080 + (b&0x0000FF) - (c&0x0000FF));
			
		}
		// {½ + a - b - c} = {½ + 2a/₂ - (a+d)/₂ - (1+a-d)/₂} = {(1+2a-a-d-1-a+d)/₂} = 0
		private static boolean isValid(int a, int b, int c, int d) {
			//              1/2      +  a           -  b           -  c
			int checksum = (0x800000 + (a&0xFF0000) - (b&0xFF0000) - (c&0xFF0000))
			             + (0x008000 + (a&0x00FF00) - (b&0x00FF00) - (c&0x00FF00))
			             + (0x000080 + (a&0x0000FF) - (b&0x0000FF) - (c&0x0000FF));

			logger.debug("{ " + pad0(Integer.toHexString(a), 6)
			          + " / " + pad0(Integer.toHexString(b), 6)
			          + " / " + pad0(Integer.toHexString(c), 6)
			          + " / " + pad0(Integer.toHexString(d), 6)
			          + " } -> checksum: " + pad0(Integer.toHexString(checksum), 6));
			
			int compare = (((a&0x010101)+(d&0x010101))&0x010101); // 정수연산 한계로 a+d가 홀수일 땐 1이 나옴
			
			if (checksum == compare) {
				return true;
			} else {
				logger.debug("is not " + pad0(Integer.toHexString(compare), 6));
				return false;
			}
		}
		
		// 서브픽셀 섞어보려고 시도해봤는게 결과물이 썩 차별점이 없음...
		@SuppressWarnings("unused")
		private static int mix(int r, int g, int b) {
			return (r&0xFF0000) | (g&0xFF00) | (b&0xFF);
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
		 * @param minWidth: 최소 폭
		 * @return 비트맵 이미지
		 * @throws Exception
		 */
		public BufferedImage toBitmap(int minWidth) throws Exception {
			switch (type) {
			case TYPE_149:
				return toBitmap149(minWidth);
			case TYPE_238:
				return toBitmap238(minWidth);
			}
			return toBitmap114(minWidth);
		}
		/**
		 * 이미지1:컨테이너1:결과물4 이미지로 변환
		 * 
		 * @param minWidth: 최소 폭
		 * @return 비트맵 이미지
		 * @throws Exception
		 */
		public BufferedImage toBitmap114(int minWidth) throws Exception {
			return toBitmap114(minWidth, 0, new int[0]);
		}
		/**
		 * 이미지1:컨테이너1:결과물4 이미지로 변환
		 * 
		 * @param minWidth: 최소 폭
		 * @param key: 암호화 키
		 * @return 비트맵 이미지
		 * @throws Exception
		 */
		public BufferedImage toBitmap114(int minWidth, String key) throws Exception {
			return toBitmap114(minWidth, getShift(key), getXors(key));
		}
		/**
		 * 이미지1:컨테이너1:결과물4 이미지로 변환
		 * 
		 * @param minWidth: 최소 폭
		 * @param shift: 출력물 픽셀 밀기
		 * @param xors: 출력물 xor 연산 수행
		 * @return 비트맵 이미지
		 * @throws Exception
		 */
		public BufferedImage toBitmap114(int minWidth, int shift, int[] xors) throws Exception {
			logger.info("\nWithTarget.toBitmap114");
			BufferedImage dataImage = toBitmapTwice(containers, minWidth / 2, getRatio(), shift, xors);
			int w = dataImage.getWidth(), h = dataImage.getHeight();
			
			// 이미지를 데이터와 같은 크기로 조절
			BufferedImage resizedTargetImage = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
			Graphics graphics = resizedTargetImage.getGraphics();
			graphics.drawImage(targetImage.getScaledInstance(w, h, Image.SCALE_SMOOTH), 0, 0, null);
			graphics.dispose();
			
			/*
			 * a: resizedTargetImage 1x1
			 * d: dataImage          1x1
			 * 
			 *  (2*x  ,2*y  )  │  (2*x+1,2*y  )
			 *        a        │    (a+d)/₂
			 * ────────┼───────
			 *  (2*x  ,2*y+1)  │  (2*x+1,2*y+1)
			 *    (1+a-d)/₂    │        a
			 * 
			 * 출력: a b
			 *       c a
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
					result.setRGB(2*x+1, 2*y  , getB(a, d));
					result.setRGB(2*x  , 2*y+1, getC(a, d));
					result.setRGB(2*x+1, 2*y+1, a);
				}
			}
			
			return result;
		}
		/**
		 * 이미지1:컨테이너4:결과물9 이미지로 변환
		 * 
		 * @param minWidth: 최소 폭
		 * @return 비트맵 이미지
		 * @throws Exception
		 */
		public BufferedImage toBitmap149(int minWidth) throws Exception {
			return toBitmap149(minWidth, 0, new int[0]);
		}
		/**
		 * 이미지1:컨테이너4:결과물9 이미지로 변환
		 * 
		 * @param minWidth: 최소 폭
		 * @param key: 암호화 키
		 * @return 비트맵 이미지
		 * @throws Exception
		 */
		public BufferedImage toBitmap149(int minWidth, String key) throws Exception {
			return toBitmap149(minWidth, getShift(key), getXors(key));
		}
		/**
		 * 이미지1:컨테이너4:결과물9 이미지로 변환
		 * 
		 * @param minWidth: 최소 폭
		 * @param shift: 출력물 픽셀 밀기
		 * @param xors: 출력물 xor 연산 수행
		 * @return 비트맵 이미지
		 * @throws Exception
		 */
		public BufferedImage toBitmap149(int minWidth, int shift, int[] xors) throws Exception {
			logger.info("\nWithTarget.toBitmap149");
			BufferedImage dataImage = toBitmapTwice(containers, (minWidth + 2) / 3 * 2, getRatio(), 2, shift, xors);
			int w = dataImage.getWidth() / 2, h = dataImage.getHeight() / 2;
			
			// 이미지를 데이터의 절반 크기로 조절
			BufferedImage resizedTargetImage = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
			Graphics graphics = resizedTargetImage.getGraphics();
			graphics.drawImage(targetImage.getScaledInstance(w, h, Image.SCALE_SMOOTH), 0, 0, null);
			graphics.dispose();
			
			/*
			 * a: resizedTargetImage 1x1
			 * d: dataImage          2x2
			 * 
			 * 출력: b1 c1 b2
			 *       c4 ａ c2
			 *       b4 c3 b3
			 * 
			 * 9픽셀 합계: 5a + 2 -> 평균 내면 a 값에 따라 2/9~7/9 값을 갖게 됨
			 */
			
			BufferedImage result = new BufferedImage(w*3, h*3, BufferedImage.TYPE_3BYTE_BGR);
			int a, d1, d2, d3, d4;
			
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					a  = 0xFFFFFF & resizedTargetImage.getRGB(x, y);
					d1 = 0xFFFFFF & dataImage.getRGB(2*x  , 2*y  );
					d2 = 0xFFFFFF & dataImage.getRGB(2*x+1, 2*y  );
					d3 = 0xFFFFFF & dataImage.getRGB(2*x+1, 2*y+1);
					d4 = 0xFFFFFF & dataImage.getRGB(2*x  , 2*y+1);
					
					result.setRGB(3*x+1, 3*y+1, a);
					result.setRGB(3*x  , 3*y  , getB(a, d1));
					result.setRGB(3*x+1, 3*y  , getC(a, d1));
					result.setRGB(3*x+2, 3*y  , getB(a, d2));
					result.setRGB(3*x+2, 3*y+1, getC(a, d2));
					result.setRGB(3*x+2, 3*y+2, getB(a, d3));
					result.setRGB(3*x+1, 3*y+2, getC(a, d3));
					result.setRGB(3*x  , 3*y+2, getB(a, d4));
					result.setRGB(3*x  , 3*y+1, getC(a, d4));
				}
			}
			
			return result;
		}
		/**
		 * 이미지2:컨테이너3:결과물8 이미지로 변환
		 * 
		 * @param minWidth: 최소 폭
		 * @return 비트맵 이미지
		 * @throws Exception
		 */
		public BufferedImage toBitmap238(int minWidth) throws Exception {
			return toBitmap238(minWidth, 0, new int[0]);
		}
		/**
		 * 이미지2:컨테이너3:결과물8 이미지로 변환
		 * 
		 * @param minWidth: 최소 폭
		 * @param key: 암호화 키
		 * @return 비트맵 이미지
		 * @throws Exception
		 */
		public BufferedImage toBitmap238(int minWidth, String key) throws Exception {
			return toBitmap238(minWidth, getShift(key), getXors(key));
		}
		/**
		 * 이미지2:컨테이너3:결과물8 이미지로 변환
		 * 
		 * @param minWidth: 최소 폭
		 * @param shift: 출력물 픽셀 밀기
		 * @param xors: 출력물 xor 연산 수행
		 * @return 비트맵 이미지
		 * @throws Exception
		 */
		public BufferedImage toBitmap238(int minWidth, int shift, int[] xors) throws Exception {
			logger.info("\nWithTarget.toBitmap238");
			BufferedImage dataImage = toBitmapTwice(containers, (minWidth + 1) / 2, getRatio() * 3 / 2, 3, shift, xors);
			int w = dataImage.getWidth(), h = dataImage.getHeight() / 3;
			
			// 이미지를 결과물의 절반 크기로 조절
			BufferedImage resizedTargetImage = new BufferedImage(w, h * 2, BufferedImage.TYPE_3BYTE_BGR);
			Graphics graphics = resizedTargetImage.getGraphics();
			graphics.drawImage(targetImage.getScaledInstance(w, h * 2, Image.SCALE_SMOOTH), 0, 0, null);
			graphics.dispose();
			
			/*
			 * a: resizedTargetImage 1x2
			 * d: dataImage          1x3
			 * 
			 * 출력: b1 a1
			 *       c1 b2
			 *       a2 c2
			 *       b3 c3
			 * 
			 * 8픽셀 합계: 2.5(a1+a2) + 1.5 -> 평균 내면 a 값에 따라 3/16~13/16 값을 갖게 됨
			 * 
			 */
			
			BufferedImage result = new BufferedImage(w*2, h*4, BufferedImage.TYPE_3BYTE_BGR);
			int a1, a2, a3, d1, d2, d3;
			
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					a1 = 0xFFFFFF & resizedTargetImage.getRGB(x, 2*y  );
					a2 = 0xFFFFFF & resizedTargetImage.getRGB(x, 2*y+1);
					a3 = ((((a1&0xFF0000) + (a2&0xFF0000)) >> 1) & 0xFF0000)
					   | ((((a1&0x00FF00) + (a2&0x00FF00)) >> 1) & 0x00FF00)
					   | ((((a1&0x0000FF) + (a2&0x0000FF)) >> 1) & 0x0000FF);
					d1 = 0xFFFFFF & dataImage.getRGB(x, 3*y  );
					d2 = 0xFFFFFF & dataImage.getRGB(x, 3*y+1);
					d3 = 0xFFFFFF & dataImage.getRGB(x, 3*y+2);
					
					result.setRGB(2*x+1, 4*y  , a1);
					result.setRGB(2*x  , 4*y+2, a2);
					result.setRGB(2*x  , 4*y  , getB(a1, d1));
					result.setRGB(2*x  , 4*y+1, getC(a1, d1));
					result.setRGB(2*x+1, 4*y+1, getB(a3, d2));
					result.setRGB(2*x+1, 4*y+2, getC(a3, d2));
					result.setRGB(2*x  , 4*y+3, getB(a2, d3));
					result.setRGB(2*x+1, 4*y+3, getC(a2, d3));
				}
			}
			
			return result;
		}
		/**
		 * 이미지4:컨테이너2:결과물9 이미지로 변환
		 * 
		 * @param minWidth: 최소 폭
		 * @return 비트맵 이미지
		 * @throws Exception
		 */
		public BufferedImage toBitmap429(int minWidth) throws Exception {
			return toBitmap429(minWidth, 0, new int[0]);
		}
		/**
		 * 이미지4:컨테이너2:결과물9 이미지로 변환
		 * 
		 * @param minWidth: 최소 폭
		 * @param key: 암호화 키
		 * @return 비트맵 이미지
		 * @throws Exception
		 */
		public BufferedImage toBitmap429(int minWidth, String key) throws Exception {
			return toBitmap429(minWidth, getShift(key), getXors(key));
		}
		/**
		 * 이미지4:컨테이너2:결과물9 이미지로 변환
		 * 
		 * @param minWidth: 최소 폭
		 * @param shift: 출력물 픽셀 밀기
		 * @param xors: 출력물 xor 연산 수행
		 * @return 비트맵 이미지
		 * @throws Exception
		 */
		public BufferedImage toBitmap429(int minWidth, int shift, int[] xors) throws Exception {
			logger.info("\nWithTarget.toBitmap429");
			BufferedImage dataImage = toBitmapTwice(containers, (minWidth + 2) / 3, getRatio() * 2, 2, shift, xors);
			int w = dataImage.getWidth(), h = dataImage.getHeight() / 2;
			
			// 이미지를 데이터의 2/3 크기로 조절
			BufferedImage resizedTargetImage = new BufferedImage(w * 2, h * 2, BufferedImage.TYPE_3BYTE_BGR);
			Graphics graphics = resizedTargetImage.getGraphics();
			graphics.drawImage(targetImage.getScaledInstance(w * 2, h * 2, Image.SCALE_SMOOTH), 0, 0, null);
			graphics.dispose();
			
			/*
			 * a: resizedTargetImage 2x2
			 * d: dataImage          1x2
			 * 
			 * 출력: a1  b1  aA
			 *       c1 aAB  c2
			 *       aB  b2  a2
			 * 
			 * 9픽셀 합계: 5a + 2 -> 평균 내면 a 값에 따라 1/9~8/9 값을 갖게 됨
			 */
			
			BufferedImage result = new BufferedImage(w*3, h*3, BufferedImage.TYPE_3BYTE_BGR);
			int a1, aA, aB, a2, aAB, d1, d2;
			
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					a1 = 0xFFFFFF & resizedTargetImage.getRGB(2*x  , 2*y  );
					aA = 0xFFFFFF & resizedTargetImage.getRGB(2*x+1, 2*y  );
					aB = 0xFFFFFF & resizedTargetImage.getRGB(2*x  , 2*y+1);
					a2 = 0xFFFFFF & resizedTargetImage.getRGB(2*x+1, 2*y+1);
					aAB = ((((aA&0xFF0000) + (aB&0xFF0000)) >> 1) & 0xFF0000)
					    | ((((aA&0x00FF00) + (aB&0x00FF00)) >> 1) & 0x00FF00)
					    | ((((aA&0x0000FF) + (aB&0x0000FF)) >> 1) & 0x0000FF);
					d1 = 0xFFFFFF & dataImage.getRGB(x, 2*y  );
					d2 = 0xFFFFFF & dataImage.getRGB(x, 2*y+1);
					
					result.setRGB(3*x  , 3*y  , a1);
					result.setRGB(3*x+2, 3*y  , aA);
					result.setRGB(3*x  , 3*y+2, aB);
					result.setRGB(3*x+2, 3*y+2, a2);
					result.setRGB(3*x+1, 3*y+1, aAB);
					result.setRGB(3*x+1, 3*y  , getB(a1, d1));
					result.setRGB(3*x  , 3*y+1, getC(a1, d1));
					result.setRGB(3*x+1, 3*y+2, getB(a2, d2));
					result.setRGB(3*x+2, 3*y+1, getC(a2, d2));
				}
			}
			
			return result;
		}
		
		private static final int CHECKSUM_SAMPLE_COUNT = 10; // 패리티 검증 체크섬 샘플 개수
		public static final int CAN_PROTOTYPE = 1;
		public static final int CAN_114 = 1 << TYPE_114;
		public static final int CAN_149 = 1 << TYPE_149;
		public static final int CAN_238 = 1 << TYPE_238;
		public static final int CAN_429 = 1 << TYPE_429;
		/**
		 * 패리티 검증으로 해석 가능한 알고리즘 확인
		 * @param bmp
		 * @return 가능한 알고리즘
		 */
		public static int possibility(BufferedImage bmp) {
			int result = 0;
			try { if (canPrototype(bmp)) { result |= CAN_PROTOTYPE; } } catch (Exception e) { logger.debug(e); }
			try { if (can114      (bmp)) { result |= CAN_114;       } } catch (Exception e) { logger.debug(e); }
			try { if (can149      (bmp)) { result |= CAN_149;       } } catch (Exception e) { logger.debug(e); }
			try { if (can238      (bmp)) { result |= CAN_238;       } } catch (Exception e) { logger.debug(e); }
			try { if (can429      (bmp)) { result |= CAN_429;       } } catch (Exception e) { logger.debug(e); }
			return result;
		}
		/**
		 * 1:1:4 레거시 형식 해석이 가능한지 패리티 검증
		 * @param bmp
		 * @return
		 * @throws Exception
		 */
		private static boolean canPrototype(BufferedImage bmp) throws Exception {
			logger.info("\nis it prototype?");
			
			int width = bmp.getWidth();
			int height = bmp.getHeight();
			logger.info("input size: " + width + " x " + height);
			
			try {
				// 1:1:4 결합 이미지일 경우 크기는 짝수여야 함
				if (width % 2 > 0 || height % 2 > 0) {
					return false;
				};
				
				boolean checkFailed = false;
				
				for (int i = 0; i < CHECKSUM_SAMPLE_COUNT; i++) {
					int x = (int) (Math.random() * width ) / 2;
					int y = (int) (Math.random() * height) / 2;
					
					// (2x,2y)와 (2x+1,2y+1)은 둘 다 출력물 이미지의 원본 픽셀로 같은 값이어야 함
					if ((bmp.getRGB(2*x, 2*y  ) & 0xFFFFFF) != (bmp.getRGB(2*x+1, 2*y+1) & 0xFFFFFF)) {
						checkFailed = true;
						break;
					}
					
					// (2x+1,2y)와 (2x,2y+1)은 합쳐서 0xFFFFFF가 나와야 함
					if ((bmp.getRGB(2*x, 2*y+1) & 0xFFFFFF)  + (bmp.getRGB(2*x+1, 2*y  ) & 0xFFFFFF) != 0xFFFFFF) {
						checkFailed = true;
						break;
					}
				}
				if (checkFailed) {
					logger.info("체크섬 오류 - 레거시 WithTarget 1:1:4 형식 이미지가 아님");
					return false;
				}
				
				logger.info("체크섬 통과 - 레거시 WithTarget 1:1:4 형식 가능");
				return true;
				
			} catch (Exception e) {
				logger.info("이미지 해석 실패");
				logger.debug(e);
			}
			
			return false;
		}
		/**
		 * 1:1:4 형식 해석이 가능한지 패리티 검증
		 * @param bmp
		 * @return
		 * @throws Exception
		 */
		private static boolean can114(BufferedImage bmp) throws Exception {
			logger.info("\nis it 1:1:4?");
			
			int width = bmp.getWidth();
			int height = bmp.getHeight();
			logger.info("input size: " + width + " x " + height);
			
			try {
				// 1:1:4 결합 이미지일 경우 크기는 짝수여야 함
				if (width % 2 > 0 || height % 2 > 0) {
					return false;
				}
				
				int a, b, c, d;
				
				boolean checkFailed = false;
				
				for (int i = 0; i < CHECKSUM_SAMPLE_COUNT; i++) {
					int x = (int) (Math.random() * width ) / 2;
					int y = (int) (Math.random() * height) / 2;
					logger.debug("sample(" + pad(x, 4) + ", " + pad(y, 4) + ")");
					
					// (2x,2y)와 (2x+1,2y+1)은 둘 다 출력물 이미지의 원본 픽셀로 같은 값이어야 함
					a = bmp.getRGB(2*x  , 2*y  );
					d = bmp.getRGB(2*x+1, 2*y+1);
					if (a != d) {
						logger.info("a != d");
						checkFailed = true;
						break;
					}
					
					// 패리티 검증
					b = bmp.getRGB(2*x+1, 2*y  );
					c = bmp.getRGB(2*x  , 2*y+1);
					
					if (!isValid(a, b, c, getD(b, c))) {
						checkFailed = true;
						break;
					}
				}
				if (checkFailed) {
					logger.info("체크섬 오류 - 현행 WithTarget 1:1:4 형식 이미지가 아님");
					return false;
				}
				
				logger.info("체크섬 통과 - 현행 WithTarget 1:1:4 형식 가능");
				return true;
				
			} catch (Exception e) {
				logger.info("이미지 해석 실패");
				logger.debug(e);
			}
			
			return false;
		}
		/**
		 * 1:4:9 형식 해석이 가능한지 패리티 검증
		 * @param bmp
		 * @return
		 * @throws Exception
		 */
		private static boolean can149(BufferedImage bmp) throws Exception {
			logger.info("\nis it 1:4:9?");
			
			int width = bmp.getWidth();
			int height = bmp.getHeight();
			logger.info("input size: " + width + " x " + height);
			
			try {
				// 1:4:9 결합 이미지일 경우 크기는 3의 배수여야 함
				if (width % 3 > 0 || height % 3 > 0) {
					return false;
				}
				
				int b1, c1, b2
				  , c4, a , c2
				  , b4, c3, b3;
				
				boolean checkFailed = false;
				
				for (int i = 0; i < CHECKSUM_SAMPLE_COUNT; i++) {
					int x = (int) (Math.random() * width  / 3);
					int y = (int) (Math.random() * height / 3);
					logger.debug("sample(" + pad(x, 4) + ", " + pad(y, 4) + "):");
					
					a  = bmp.getRGB(3*x+1, 3*y+1);
					b1 = bmp.getRGB(3*x  , 3*y  );
					c1 = bmp.getRGB(3*x+1, 3*y  );
					b2 = bmp.getRGB(3*x+2, 3*y  );
					c2 = bmp.getRGB(3*x+2, 3*y+1);
					b3 = bmp.getRGB(3*x+2, 3*y+2);
					c3 = bmp.getRGB(3*x+1, 3*y+2);
					b4 = bmp.getRGB(3*x  , 3*y+2);
					c4 = bmp.getRGB(3*x  , 3*y+1);
					
					// 패리티 검증
					if (!isValid(a, b1, c1, getD(b1, c1))) { checkFailed = true; break; }
					if (!isValid(a, b2, c2, getD(b2, c2))) { checkFailed = true; break; }
					if (!isValid(a, b3, c3, getD(b3, c3))) { checkFailed = true; break; }
					if (!isValid(a, b4, c4, getD(b4, c4))) { checkFailed = true; break; }
				}
				if (checkFailed) {
					logger.info("체크섬 오류 - WithTarget 1:4:9 형식 이미지가 아님");
					return false;
				}
				
				logger.info("체크섬 통과 - WithTarget 1:4:9 형식 가능");
				return true;
				
			} catch (Exception e) {
				logger.info("이미지 해석 실패");
				logger.debug(e);
			}
			
			return false;
		}
		/**
		 * 2:3:8 형식 해석이 가능한지 패리티 검증
		 * @param bmp
		 * @return
		 * @throws Exception
		 */
		private static boolean can238(BufferedImage bmp) throws Exception {
			logger.info("\nis it 2:3:8?");
			
			int width = bmp.getWidth();
			int height = bmp.getHeight();
			logger.info("input size: " + width + " x " + height);
			
			try {
				// 2:3:8 결합 이미지일 경우 크기는 6x4의 배수여야 함
				if (width % 6 > 0 || height % 4 > 0) {
					return false;
				}
				
				int b1, a1
				  , c1, b2
				  , a2, c2
				  , b3, c3, a3;
				
				boolean checkFailed = false;
				
				for (int i = 0; i < CHECKSUM_SAMPLE_COUNT; i++) {
					int x = (int) (Math.random() * width  / 2);
					int y = (int) (Math.random() * height / 4);
					logger.debug("sample(" + pad(x, 4) + ", " + pad(y, 4) + "):");
					
					a1 = bmp.getRGB(2*x+1, 4*y  );
					a2 = bmp.getRGB(2*x  , 4*y+2);
					b1 = bmp.getRGB(2*x  , 4*y  );
					c1 = bmp.getRGB(2*x  , 4*y+1);
					b2 = bmp.getRGB(2*x+1, 4*y+1);
					c2 = bmp.getRGB(2*x+1, 4*y+2);
					b3 = bmp.getRGB(2*x  , 4*y+3);
					c3 = bmp.getRGB(2*x+1, 4*y+3);
					a3 = ((((a1&0xFF0000) + (a2&0xFF0000)) >> 1) & 0xFF0000)
					   | ((((a1&0x00FF00) + (a2&0x00FF00)) >> 1) & 0x00FF00)
					   | ((((a1&0x0000FF) + (a2&0x0000FF)) >> 1) & 0x0000FF);
					
					// 패리티 검증
					if (!isValid(a1, b1, c1, getD(b1, c1))) { checkFailed = true; break; }
					if (!isValid(a3, b2, c2, getD(b2, c2))) { checkFailed = true; break; }
					if (!isValid(a2, b3, c3, getD(b3, c3))) { checkFailed = true; break; }
				}
				if (checkFailed) {
					logger.info("체크섬 오류 - WithTarget 2:3:8 형식 이미지가 아님");
					return false;
				}
				
				logger.info("체크섬 통과 - WithTarget 2:3:8 형식 가능");
				return true;
				
			} catch (Exception e) {
				logger.info("이미지 해석 실패");
				logger.debug(e);
			}
			
			return false;
		}
		/**
		 * 4:2:9 형식 해석이 가능한지 패리티 검증
		 * @param bmp
		 * @return
		 * @throws Exception
		 */
		private static boolean can429(BufferedImage bmp) throws Exception {
			logger.info("\nis it 4:2:9?");
			
			int width = bmp.getWidth();
			int height = bmp.getHeight();
			logger.info("input size: " + width + " x " + height);
			
			try {
				// 4:2:9 결합 이미지일 경우 크기는 3의 배수여야 함
				if (width % 3 > 0 || height % 3 > 0) {
					return false;
				}
				
				int a1, b1, aA
				  , c1,aAB, c2
				  , aB, b2, a2;
				
				boolean checkFailed = false;
				
				for (int i = 0; i < CHECKSUM_SAMPLE_COUNT; i++) {
					int x = (int) (Math.random() * width  / 3);
					int y = (int) (Math.random() * height / 3);
					logger.debug("sample(" + pad(x, 4) + ", " + pad(y, 4) + "):");
					
					a1 = bmp.getRGB(3*x  , 3*y  );
					aA = bmp.getRGB(3*x+2, 3*y  );
					aB = bmp.getRGB(3*x  , 3*y+2);
					a2 = bmp.getRGB(3*x+2, 3*y+2);
					aAB= bmp.getRGB(3*x+1, 3*y+1) & 0xFFFFFF;
					b1 = bmp.getRGB(3*x+1, 3*y  );
					c1 = bmp.getRGB(3*x  , 3*y+1);
					b2 = bmp.getRGB(3*x+1, 3*y+2);
					c2 = bmp.getRGB(3*x+2, 3*y+1);
					
					// 패리티 검증
					if (!isValid(a1, b1, c1, getD(b1, c1))) { checkFailed = true; break; }
					if (!isValid(a2, b2, c2, getD(b2, c2))) { checkFailed = true; break; }
					if (aAB != (((((aA&0xFF0000) + (aB&0xFF0000)) >> 1) & 0xFF0000)
						      | ((((aA&0x00FF00) + (aB&0x00FF00)) >> 1) & 0x00FF00)
						      | ((((aA&0x0000FF) + (aB&0x0000FF)) >> 1) & 0x0000FF))) {
						logger.debug("aAB != (aA+aB)/2");
						checkFailed = true; break;
					}
				}
				if (checkFailed) {
					logger.info("체크섬 오류 - WithTarget 4:2:9 형식 이미지가 아님");
					return false;
				}
				
				logger.info("체크섬 통과 - WithTarget 4:2:9 형식 가능");
				return true;
				
			} catch (Exception e) {
				logger.info("이미지 해석 실패");
				logger.debug(e);
			}
			
			return false;
		}
		
		/**
		 * 1:1:4 레거시 형식 비트맵 이미지를 해석
		 * @param bmp
		 * @return
		 * @throws Exception
		 */
		@Deprecated
		private static WithTarget fromBitmapPrototype(BufferedImage bmp, boolean tryWithout) {
			logger.warn("\nWithTarget.fromBitmapPrototype - 개발 도중 레거시 형식 지원");

			int width  = bmp.getWidth();
			int height = bmp.getHeight();
			logger.info("input size: " + width + " x " + height);
			
			int xSize = width / 2, ySize = height / 2;
			
			try {
				BufferedImage targetImage = new BufferedImage(xSize, ySize, BufferedImage.TYPE_3BYTE_BGR);
				BufferedImage dataImage   = new BufferedImage(xSize, ySize, BufferedImage.TYPE_3BYTE_BGR);
				
				for (int y = 0; y < ySize; y++) {
					for (int x = 0; x < xSize; x++) {
						targetImage.setRGB(x, y, bmp.getRGB(2*x  , 2*y));
						dataImage  .setRGB(x, y, bmp.getRGB(2*x+1, 2*y));
					}
				}
				List<Container> containers = Container.fromBitmap(dataImage);
				if (containers.size() > 0) {
					return new WithTarget(targetImage, containers, TYPE_114);
				}
				
			} catch (Exception e) {
				logger.info("이미지 해석 실패");
				logger.debug(e);
			}
			
			// 위에서 실패했을 경우 without target 진행
			if (tryWithout) {
				logger.info("Without target");
				try {
					List<Container> containers = Container.fromBitmap(bmp);
					if (containers.size() > 0) {
						return new WithTarget(null, containers, 0);
					}
				} catch (Exception e) {
					logger.info("이미지 해석 실패");
					logger.debug(e);
				}
			}
			
			return null;
		}
		/**
		 * 1:1:4 형식 비트맵 이미지를 해석
		 * @param bmp
		 * @param shift: 출력물 픽셀 밀기
		 * @param xors: 출력물 xor 연산 수행
		 * @return
		 * @throws Exception
		 */
		private static WithTarget fromBitmap114(BufferedImage bmp, int shift, int[] xors) {
			logger.info("\nWithTarget.fromBitmap 1:1:4");
			
			int width  = bmp.getWidth();
			int height = bmp.getHeight();
			logger.info("input size: " + width + " x " + height);
			
			int xSize = width / 2, ySize = height / 2;
			
			try {
				int a, b, c;
				
				BufferedImage targetImage = new BufferedImage(xSize, ySize, BufferedImage.TYPE_3BYTE_BGR);
				BufferedImage dataImage   = new BufferedImage(xSize, ySize, BufferedImage.TYPE_3BYTE_BGR);
				
				for (int y = 0; y < ySize; y++) {
					for (int x = 0; x < xSize; x++) {
						a = bmp.getRGB(2*x  , 2*y  );
						b = bmp.getRGB(2*x+1, 2*y  );
						c = bmp.getRGB(2*x  , 2*y+1);
						
						targetImage.setRGB(x, y, a);
						dataImage  .setRGB(x, y, getD(b, c));
					}
				}
				List<Container> containers = Container.fromBitmap(dataImage, shift, xors);
				if (containers.size() > 0) {
					return new WithTarget(targetImage, containers, TYPE_114);
				}
				
			} catch (Exception e) {
				logger.info("이미지 해석 실패");
				logger.debug(e);
			}
			
			return null;
		}
		/**
		 * 1:4:9 형식 비트맵 이미지를 해석
		 * @param bmp
		 * @param shift: 출력물 픽셀 밀기
		 * @param xors: 출력물 xor 연산 수행
		 * @return
		 * @throws Exception
		 */
		private static WithTarget fromBitmap149(BufferedImage bmp, int shift, int[] xors) {
			logger.info("\nWithTarget.fromBitmap 1:4:9");
			
			int width  = bmp.getWidth();
			int height = bmp.getHeight();
			logger.info("input size: " + width + " x " + height);
			
			int xSize = width / 3, ySize = height / 3;
			
			try {
				int b1, c1, b2
				  , c4, a , c2
				  , b4, c3, b3;
				
				BufferedImage targetImage = new BufferedImage(xSize  , ySize  , BufferedImage.TYPE_3BYTE_BGR);
				BufferedImage dataImage   = new BufferedImage(xSize*2, ySize*2, BufferedImage.TYPE_3BYTE_BGR);
				
				for (int y = 0; y < ySize; y++) {
					for (int x = 0; x < xSize; x++) {
						a  = bmp.getRGB(3*x+1, 3*y+1);
						b1 = bmp.getRGB(3*x  , 3*y  );
						c1 = bmp.getRGB(3*x+1, 3*y  );
						b2 = bmp.getRGB(3*x+2, 3*y  );
						c2 = bmp.getRGB(3*x+2, 3*y+1);
						b3 = bmp.getRGB(3*x+2, 3*y+2);
						c3 = bmp.getRGB(3*x+1, 3*y+2);
						b4 = bmp.getRGB(3*x  , 3*y+2);
						c4 = bmp.getRGB(3*x  , 3*y+1);
						
						targetImage.setRGB(x, y, a);
						dataImage.setRGB(2*x  , 2*y  , getD(b1, c1));
						dataImage.setRGB(2*x+1, 2*y  , getD(b2, c2));
						dataImage.setRGB(2*x+1, 2*y+1, getD(b3, c3));
						dataImage.setRGB(2*x  , 2*y+1, getD(b4, c4));
					}
				}
				List<Container> containers = Container.fromBitmap(dataImage, shift, xors);
				if (containers.size() > 0) {
					return new WithTarget(targetImage, containers, TYPE_149);
				}
				
			} catch (Exception e) {
				logger.info("이미지 해석 실패");
				logger.debug(e);
			}
			
			return null;
		}
		/**
		 * 2:3:8 형식 비트맵 이미지를 해석
		 * @param bmp
		 * @param shift: 출력물 픽셀 밀기
		 * @param xors: 출력물 xor 연산 수행
		 * @return
		 * @throws Exception
		 */
		private static WithTarget fromBitmap238(BufferedImage bmp, int shift, int[] xors) {
			logger.info("\nWithTarget.fromBitmap 2:3:8");
			
			int width  = bmp.getWidth();
			int height = bmp.getHeight();
			logger.info("input size: " + width + " x " + height);
			
			int xSize = width / 2, ySize = height / 4;
			
			try {
				int b1, a1
				  , c1, b2
				  , a2, c2
				  , b3, c3;
				
				BufferedImage targetImage = new BufferedImage(xSize, ySize*2, BufferedImage.TYPE_3BYTE_BGR);
				BufferedImage dataImage   = new BufferedImage(xSize, ySize*3, BufferedImage.TYPE_3BYTE_BGR);
				
				for (int y = 0; y < ySize; y++) {
					for (int x = 0; x < xSize; x++) {
						a1 = bmp.getRGB(2*x+1, 4*y  );
						a2 = bmp.getRGB(2*x  , 4*y+2);
						b1 = bmp.getRGB(2*x  , 4*y  );
						c1 = bmp.getRGB(2*x  , 4*y+1);
						b2 = bmp.getRGB(2*x+1, 4*y+1);
						c2 = bmp.getRGB(2*x+1, 4*y+2);
						b3 = bmp.getRGB(2*x  , 4*y+3);
						c3 = bmp.getRGB(2*x+1, 4*y+3);
						
						targetImage.setRGB(x, 2*y  , a1);
						targetImage.setRGB(x, 2*y+1, a2);
						dataImage.setRGB(x, 3*y  , getD(b1, c1));
						dataImage.setRGB(x, 3*y+1, getD(b2, c2));
						dataImage.setRGB(x, 3*y+2, getD(b3, c3));
					}
				}
				List<Container> containers = Container.fromBitmap(dataImage, shift, xors);
				if (containers.size() > 0) {
					return new WithTarget(targetImage, containers, TYPE_238);
				}
				
			} catch (Exception e) {
				logger.info("이미지 해석 실패");
				logger.debug(e);
			}
			
			return null;
		}
		/**
		 * 4:2:9 형식 비트맵 이미지를 해석
		 * @param bmp
		 * @param shift: 출력물 픽셀 밀기
		 * @param xors: 출력물 xor 연산 수행
		 * @return
		 * @throws Exception
		 */
		private static WithTarget fromBitmap429(BufferedImage bmp, int shift, int[] xors) {
			logger.info("\nWithTarget.fromBitmap 4:2:9");
			
			int width  = bmp.getWidth();
			int height = bmp.getHeight();
			logger.info("input size: " + width + " x " + height);
			
			int xSize = width / 3, ySize = height / 3;
			
			try {
				int a1, b1, aA
				  , c1,     c2
				  , aB, b2, a2;
				
				BufferedImage targetImage = new BufferedImage(xSize*2, ySize*2, BufferedImage.TYPE_3BYTE_BGR);
				BufferedImage dataImage   = new BufferedImage(xSize  , ySize*2, BufferedImage.TYPE_3BYTE_BGR);
				
				for (int y = 0; y < ySize; y++) {
					for (int x = 0; x < xSize; x++) {
						a1 = bmp.getRGB(3*x  , 3*y  );
						aA = bmp.getRGB(3*x+2, 3*y  );
						aB = bmp.getRGB(3*x  , 3*y+2);
						a2 = bmp.getRGB(3*x+2, 3*y+2);
						b1 = bmp.getRGB(3*x+1, 3*y  );
						c1 = bmp.getRGB(3*x  , 3*y+1);
						b2 = bmp.getRGB(3*x+1, 3*y+2);
						c2 = bmp.getRGB(3*x+2, 3*y+1);

						targetImage.setRGB(2*x  , 2*y  , a1);
						targetImage.setRGB(2*x+1, 2*y  , aA);
						targetImage.setRGB(2*x  , 2*y+1, aB);
						targetImage.setRGB(2*x+1, 2*y+1, a2);
						dataImage.setRGB(x, 2*y  , getD(b1, c1));
						dataImage.setRGB(x, 2*y+1, getD(b2, c2));
					}
				}
				List<Container> containers = Container.fromBitmap(dataImage, shift, xors);
				if (containers.size() > 0) {
					return new WithTarget(targetImage, containers, TYPE_429);
				}
				
			} catch (Exception e) {
				logger.info("이미지 해석 실패");
				logger.debug(e);
			}
			
			return null;
		}
		/**
		 * 비트맵 이미지를 현존하는 알고리즘으로 차례로 해석 시도
		 * @param bmp
		 * @return
		 * @throws Exception
		 */
		public static WithTarget fromBitmap(BufferedImage bmp) {
			return fromBitmap(bmp, possibility(bmp));
		}
		/**
		 * 비트맵 이미지를 키값에 따라 변환 후, 현존하는 알고리즘으로 차례로 해석 시도
		 * @param bmp
		 * @param key
		 * @return
		 * @throws Exception
		 */
		public static WithTarget fromBitmap(BufferedImage bmp, String key) {
			return fromBitmap(bmp, possibility(bmp), key);
		}
		/**
		 * 비트맵 이미지를 주어진 값에 따라 변환 후, 현존하는 알고리즘으로 차례로 해석 시도
		 * @param bmp
		 * @param shift
		 * @param xors
		 * @return
		 * @throws Exception
		 */
		public static WithTarget fromBitmap(BufferedImage bmp, int shift, int[] xors) {
			return fromBitmap(bmp, possibility(bmp), shift, xors);
		}
		/**
		 * 비트맵 이미지를 주어진 알고리즘으로 차례로 해석 시도
		 * @param bmp
		 * @param possibility
		 * @return
		 * @throws Exception
		 */
		public static WithTarget fromBitmap(BufferedImage bmp, int possibility) {
			return fromBitmap(bmp, possibility(bmp), 0, new int[0]);
		}
		/**
		 * 비트맵 이미지를 키값에 따라 변환 후, 주어진 알고리즘으로 차례로 해석 시도
		 * @param bmp
		 * @param possibility
		 * @param key
		 * @return
		 * @throws Exception
		 */
		public static WithTarget fromBitmap(BufferedImage bmp, int possibility, String key) {
			return fromBitmap(bmp, possibility(bmp), getShift(key), getXors(key));
		}
		/**
		 * 비트맵 이미지를 주어진 값에 따라 변환 후, 주어진 알고리즘으로 차례로 해석 시도
		 * @param bmp
		 * @param possibility
		 * @param shift
		 * @param xors
		 * @return
		 * @throws Exception
		 */
		public static WithTarget fromBitmap(BufferedImage bmp, int possibility, int shift, int[] xors) {
			logger.info("\nWithTarget.fromBitmap");
			WithTarget result;
			
			// 1:1:4 형식으로 시도
			if (((possibility & CAN_114) > 0) && (result = fromBitmap114(bmp, shift, xors)) != null) {
				return result;
			}
			// 1:4:9 형식으로 시도
			if (((possibility & CAN_149) > 0) && (result = fromBitmap149(bmp, shift, xors)) != null) {
				return result;
			}
			// 2:3:8 형식으로 시도
			if (((possibility & CAN_238) > 0) && (result = fromBitmap238(bmp, shift, xors)) != null) {
				return result;
			}
			// 4:2:9 형식으로 시도
			if (((possibility & CAN_429) > 0) && (result = fromBitmap429(bmp, shift, xors)) != null) {
				return result;
			}
			
			logger.info("Without target 해석 시도");
			try {
				List<Container> containers = Container.fromBitmap(bmp, shift, xors);
				if (containers.size() > 0) {
					return new WithTarget(null, containers, 0);
				}
			} catch (Exception e) {
				logger.error(e);
			}
			
			if ((possibility & CAN_PROTOTYPE) > 0) {
				logger.info("현행 방식으로 해석 실패했을 경우, 개발 도중 레거시 형식으로 재시도");
				return fromBitmapPrototype(bmp, false);
			}
			return null;
		}
	}
}