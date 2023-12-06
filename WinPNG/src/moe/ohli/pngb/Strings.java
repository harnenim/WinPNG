package moe.ohli.pngb;

import java.util.HashMap;
import java.util.Map;

/**
 * 
 * @author harne_
 *
 */
public class Strings {
	
	public enum Language {
		KR, EN, JP
	}

	private static final Map<String, String> KR = new HashMap<>();
	static {
		KR.put("open placeholder", "이미지 파일을 드래그해서 열 수 있습니다.");
		KR.put("open", "열기");
		KR.put("close", "닫기");
		
		KR.put("add", "추가");
		KR.put("delete", "삭제");
		KR.put("export placeholder", "목록에서 파일 선택 후 탐색기로 드래그, 혹은 파일을 추출할 경로 입력");
		KR.put("export", "추출");
		
		KR.put("target tooltip", "Ctrl+V로 이미지를 적용할 수 있습니다.");
		KR.put("output tooltip", "Ctrl+C로 복사할 수 있습니다.");
		KR.put("input image", "입력 이미지");
		KR.put("dont use", "사용 안 함");
		KR.put("output image", "출력 이미지");
		KR.put("min width", "최소 폭");
		KR.put("save", "저장");
		KR.put("copy", "복사");
		
		KR.put("FileFilter-PNG", "PNG 파일(*.png)");

		KR.put("warn-width-input", "올바른 크기를 입력하세요.");
		KR.put("warn-width-large", "최소 크기가 너무 큽니다.\n1920 이하의 값을 적어주세요.");
		
		KR.put("cant parse", "해석할 수 없는 파일입니다.");
		KR.put("open png", "PNG 파일 열기");
		KR.put("save png", "PNG 파일 저장");
		KR.put("cant save", "저장되지 않았습니다.");
		KR.put("empty image", "이미지로 저장할 내용이 없습니다.");
		KR.put("image copied", "이미지가 클립보드에 복사됐습니다.");
		KR.put("clipboard fail", "클립보드에 접근하지 못했습니다.");
		
		KR.put("file duplicated title", "파일 중복");
		KR.put("file duplicated message", "파일 경로가 중복됩니다.\n덮어쓰시겠습니끼?\n");
		
		KR.put("cant export to temp dir", "임시 파일 경로에는 추출할 수 없습니다.");
		KR.put("file not selected", "선택된 파일이 없습니다.");
		KR.put("file exported", "파일을 추출했습니다.");
		KR.put("cant export file", "파일을 추출하지 못했습니다.");
		KR.put("cant parse path", "해석할 수 없는 이미지 경로입니다.");

		KR.put("open image", "이미지 파일 열기");
		KR.put("confirm set image", "이미지를 적용하시겠습니까?");
		KR.put("confirm close file", "현재 파일을 닫겠습니까?");
		KR.put("confirm add file", "파일 목록에 추가하시겠습니까?");
		KR.put("cant parse png and add to list", "해석할 수 없는 PNG 파일입니다.\n파일 목록에 추가합니다.");
	}
	private static final Map<String, String> EN = new HashMap<>();
	static {
		EN.put("open placeholder", "You can open image by drag and drop.");
		EN.put("open", "Open");
		EN.put("close", "Close");
		
		EN.put("add", "Add");
		EN.put("delete", "Delete");
		EN.put("export placeholder", "Select file(s) and drag to explorer, or input export path.");
		EN.put("export", "Export");
		
		EN.put("target tooltip", "You can set image by Ctrl+V.");
		EN.put("output tooltip", "You can copy image by Ctrl+C.");
		EN.put("input image", "Input Image");
		EN.put("dont use", "Don't use");
		EN.put("output image", "Output Image");
		EN.put("min width", "Min-Width");
		EN.put("save", "Save");
		EN.put("copy", "Copy");
		
		EN.put("FileFilter-PNG", "PNG File(*.png)");

		EN.put("warn-width-input", "Input correct size.");
		EN.put("warn-width-large", "Too large width.\nYou can input under 1920.");
		
		EN.put("cant parse", "Can't parse the file.");
		EN.put("open png", "Open PNG file");
		EN.put("save png", "Save PNG file");
		EN.put("cant save", "Can't save the file.");
		EN.put("empty image", "There is no image to save.");
		EN.put("image copied", "The image copied to the clipboard.");
		EN.put("clipboard fail", "Can't access to the clipboard.");
		
		EN.put("file duplicated title", "File duplicated");
		EN.put("file duplicated message", "The path of file is duplicated.\nOverwrite it?\n");
		
		EN.put("cant export to temp dir", "You can't export the file(s) to the temp directory.");
		EN.put("file not selected", "File(s) not selected.");
		EN.put("file exported", "File exported.");
		EN.put("cant export file", "Can't export the file(s).");
		EN.put("cant parse path", "Can't parse the file of the path.");

		EN.put("open image", "Open image file");
		EN.put("confirm set image", "Do you set the target image?");
		EN.put("confirm close file", "Do you want close the opened file?");
		EN.put("confirm add file", "Do you want add the file(s) to the list?");
		EN.put("cant parse png and add to list", "Can't parse the PNG file.\nThe file will add to the list.");
	}
	private static final Map<String, String> JP = new HashMap<>();
	static {
	}
	
	private static Map<String, String> selected = EN;
	public static void setLanguage(Language lang) {
		switch (lang) {
			case KR: selected = KR; break;
			case EN: selected = EN; break;
			case JP: selected = JP; break;
			default: selected = KR; break;
		}
	}
	public static String get(String key) {
		String value = selected.get(key);
		return (value == null) ? key : value;
	}
}
