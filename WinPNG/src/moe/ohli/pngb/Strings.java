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
	private static final Map<String, String> EN = new HashMap<>();
	static {
		EN.put("이미지 파일을 드래그해서 열 수 있습니다."               , "You can open image by drag and drop.");
		EN.put("열기"                                                   , "Open");
		EN.put("닫기"                                                   , "Close");
		
		EN.put("추가"                                                   , "Add");
		EN.put("전체 선택"                                              , "Select All");
		EN.put("삭제"                                                   , "Delete");
		EN.put("목록에서 파일 선택 후 탐색기로 드래그, 혹은 파일을 추출할 경로 입력", "Select file(s) and drag to explorer, or input export path.");
		EN.put("추출"                                                   , "Export");
		
		EN.put("Ctrl+V로 이미지를 적용할 수 있습니다."                  , "You can set image by Ctrl+V.");
		EN.put("Ctrl+C로 복사할 수 있습니다."                           , "You can copy image by Ctrl+C.");
		EN.put("입력 이미지"                                            , "Input Image");
		EN.put("사용 안 함"                                             , "Don't use");
		EN.put("비율"                                                   , "Ratio");
		EN.put("출력 이미지"                                            , "Output Image");
		EN.put("비밀번호 걸기"                                          , "Set password");
		EN.put("최소 폭"                                                , "Min-Width");
		EN.put("저장"                                                   , "Save");
		EN.put("복사"                                                   , "Copy");

		EN.put("PNG 파일(*.png)"                                        , "PNG File(*.png)");

		EN.put("올바른 크기를 입력하세요."                              , "Input correct size.");
		EN.put("최소 크기가 너무 큽니다.\n1920 이하의 값을 적어주세요." , "Too large width.\nYou can input under 1920.");

		EN.put("해석할 수 없는 파일입니다."                             , "Can't parse the file.");
		EN.put("PNG 파일 열기"                                          , "Open PNG file");
		EN.put("PNG 파일 저장"                                          , "Save PNG file");
		EN.put("저장되지 않았습니다."                                   , "Can't save the file.");
		EN.put("이미지로 저장할 내용이 없습니다."                       , "There is no image to save.");
		EN.put("이미지가 클립보드에 복사됐습니다."                      , "The image copied to the clipboard.");
		EN.put("클립보드에 접근하지 못했습니다."                        , "Can't access to the clipboard.");
                                                                       
		EN.put("파일 중복"                                              , "File duplicated");
		EN.put("파일 경로가 중복됩니다.\n덮어쓰시겠습니까?"             , "The path of file is duplicated.\nOverwrite it?");

		EN.put("임시 파일 경로에는 추출할 수 없습니다."                 , "You can't export the file(s) to the temp directory.");
		EN.put("선택된 파일이 없습니다."                                , "File(s) not selected.");
		EN.put("파일을 추출했습니다."                                   , "File(s) exported.");
		EN.put("파일을 추출하지 못했습니다."                            , "Can't export the file(s).");
		EN.put("해석할 수 없는 이미지 경로입니다."                      , "Can't parse the file of the path.");

		EN.put("이미지 파일 열기"                                       , "Open image file");
		EN.put("이미지를 적용하시겠습니까?"                             , "Do you set the target image?");
		EN.put("이미지를 적용할 수 없습니다."                           , "Can't set the image.");
		EN.put("해석할 수 없는 파일입니다."                             , "Can't parse the file.");
		EN.put("현재 파일을 닫겠습니까?"                                , "Do you want close the opened file?");
		EN.put("파일 목록에 추가하시겠습니까?"                          , "Do you want add the file(s) to the list?");
		EN.put("해석할 수 없는 PNG 파일입니다.\n파일 목록에 추가합니다.", "Can't parse the PNG file.\nThe file will add to the list.");
		
		EN.put("클립보드의 이미지를 가져옵니다."                        , "Do you want ead the image from clipboard.");
		EN.put("이미지 붙여넣기"                                        , "Paste the image");
	}
	private static final Map<String, String> JP = new HashMap<>();
	static {
		JP.put("이미지 파일을 드래그해서 열 수 있습니다."               , "画像ファイルをドラッグして開くことができます。");
		JP.put("열기"                                                   , "開く");
		JP.put("닫기"                                                   , "閉じる");
		
		JP.put("추가"                                                   , "追加");
		JP.put("전체 선택"                                              , "全て選択");
		JP.put("삭제"                                                   , "削除");
		JP.put("목록에서 파일 선택 후 탐색기로 드래그, 혹은 파일을 추출할 경로 입력", "リストからファイルを選択しエクスポートする経路を入力。");
		JP.put("추출"                                                   , "エクスポート");
		
		JP.put("Ctrl+V로 이미지를 적용할 수 있습니다."                  , "Ctrl+Vで画像を適用できます。");
		JP.put("Ctrl+C로 복사할 수 있습니다."                           , "Ctrl+Cでコピーできます。");
		JP.put("입력 이미지"                                            , "入力画像");
		JP.put("사용 안 함"                                             , "使わない");
		JP.put("비율"                                                   , "比率");
		JP.put("출력 이미지"                                            , "出力画像");
		JP.put("비밀번호 걸기"                                          , "パスワード設定");
		JP.put("최소 폭"                                                , "最低の幅");
		JP.put("저장"                                                   , "保存");
		JP.put("복사"                                                   , "コピー");

		JP.put("PNG 파일(*.png)"                                        , "PNGファイル(*.png)");

		JP.put("올바른 크기를 입력하세요."                              , "正しいサイズを入力してください。");
		JP.put("최소 크기가 너무 큽니다.\n1920 이하의 값을 적어주세요." , "最低のサイズが大きすぎます。\n1920以下の数字を入力してください。");

		JP.put("해석할 수 없는 파일입니다."                             , "解析できないファイルです。");
		JP.put("PNG 파일 열기"                                          , "PNGファイル開き");
		JP.put("PNG 파일 저장"                                          , "PNGファイル保存");
		JP.put("저장되지 않았습니다."                                   , "保存失敗。");
		JP.put("이미지로 저장할 내용이 없습니다."                       , "保存する対象がありません。");
		JP.put("이미지가 클립보드에 복사됐습니다."                      , "画像がクリップボードにコピーされました。");
		JP.put("클립보드에 접근하지 못했습니다."                        , "クリップボードに接近することができませんでした。");
                                                                       
		JP.put("파일 중복"                                              , "ファイル重ね");
		JP.put("파일 경로가 중복됩니다.\n덮어쓰시겠습니까?"             , "ファイルの経路が重なります。\n上書きしますか？");

		JP.put("임시 파일 경로에는 추출할 수 없습니다."                 , "臨時ファイル経路にはエクスポートできません。");
		JP.put("선택된 파일이 없습니다."                                , "選択されたファイルがありません。");
		JP.put("파일을 추출했습니다."                                   , "ファイルをエクスポートしました。");
		JP.put("파일을 추출하지 못했습니다."                            , "ファイルをエクスポートできませんでした。");
		JP.put("해석할 수 없는 이미지 경로입니다."                      , "解析できない画像の経路です。");

		JP.put("이미지 파일 열기"                                       , "画像ファイル開き");
		JP.put("이미지를 적용하시겠습니까?"                             , "画像を適用しますか？");
		JP.put("이미지를 적용할 수 없습니다."                           , "画像を適用できません。");
		JP.put("해석할 수 없는 파일입니다."                             , "解析できないファイルです。");
		JP.put("현재 파일을 닫겠습니까?"                                , "現在のファイルを閉じますか？");
		JP.put("파일 목록에 추가하시겠습니까?"                          , "ファイルリストに追加しますか？");
		JP.put("해석할 수 없는 PNG 파일입니다.\n파일 목록에 추가합니다.", "解析できないPNGファイルです。\nファイルリストに追加します。");
		
		JP.put("클립보드의 이미지를 가져옵니다."                        , "クリップボードから画像を読み込みます。");
		JP.put("이미지 붙여넣기"                                        , "画像ペースト");
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
