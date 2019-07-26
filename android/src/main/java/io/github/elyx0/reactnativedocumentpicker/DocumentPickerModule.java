package io.github.elyx0.reactnativedocumentpicker;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import com.classtinginc.file_picker.consts.Extra;
import com.classtinginc.file_picker.consts.TranslationKey;
import com.classtinginc.file_picker.model.File;
import com.classtinginc.file_picker.FilePicker;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.google.gson.Gson;
import android.webkit.MimeTypeMap;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import java.util.HashMap;

/**
 * @see <a href="https://developer.android.com/guide/topics/providers/document-provider.html">android documentation</a>
 */
public class DocumentPickerModule extends ReactContextBaseJavaModule {
	private static final String NAME = "RNDocumentPicker";
	private static final int READ_REQUEST_CODE = 41;

	private static final String E_ACTIVITY_DOES_NOT_EXIST = "ACTIVITY_DOES_NOT_EXIST";
	private static final String E_FAILED_TO_SHOW_PICKER = "FAILED_TO_SHOW_PICKER";
	private static final String E_DOCUMENT_PICKER_CANCELED = "DOCUMENT_PICKER_CANCELED";
	private static final String E_UNABLE_TO_OPEN_FILE_TYPE = "UNABLE_TO_OPEN_FILE_TYPE";
	private static final String E_UNKNOWN_ACTIVITY_RESULT = "UNKNOWN_ACTIVITY_RESULT";
	private static final String E_INVALID_DATA_RETURNED = "INVALID_DATA_RETURNED";
	private static final String E_UNEXPECTED_EXCEPTION = "UNEXPECTED_EXCEPTION";

	private static final String OPTION_TYPE = "type";
	private static final String OPTION_MULIPLE = "multiple";
	private static final String OPTION_MAX_FILES_COUNT = "maxFilesCount";
	private static final String OPTION_MAX_FILE_SIZE = "maxFileSize";
	private static final String OPTION_TRANSLATIONS = "translations";

	private static final String FIELD_URI = "uri";
	private static final String FIELD_NAME = "name";
	private static final String FIELD_TYPE = "type";
	private static final String FIELD_SIZE = "size";

	private final ActivityEventListener activityEventListener = new BaseActivityEventListener() {
		@Override
		public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
			if (requestCode == READ_REQUEST_CODE) {
				if (promise != null) {
					onShowActivityResult(resultCode, data, promise);
					promise = null;
				}
			}
		}
	};

	private String[] readableArrayToStringArray(ReadableArray readableArray) {
		int l = readableArray.size();
		String[] array = new String[l];
		for (int i = 0; i < l; ++i) {
			array[i] = readableArray.getString(i);
		}
		return array;
	}

	private Promise promise;

	public DocumentPickerModule(ReactApplicationContext reactContext) {
		super(reactContext);
		reactContext.addActivityEventListener(activityEventListener);
	}

	@Override
	public void onCatalystInstanceDestroy() {
		super.onCatalystInstanceDestroy();
		getReactApplicationContext().removeActivityEventListener(activityEventListener);
	}

	@Override
	public String getName() {
		return NAME;
	}

	@ReactMethod
	public void pick(ReadableMap args, Promise promise) {
		Activity currentActivity = getCurrentActivity();

		if (currentActivity == null) {
			promise.reject(E_ACTIVITY_DOES_NOT_EXIST, "Current activity does not exist");
			return;
		}

		this.promise = promise;

		try {
			boolean multiple = args.hasKey(OPTION_MULIPLE) && args.getBoolean(OPTION_MULIPLE);
			int maxFilesCount = args.hasKey(OPTION_MAX_FILES_COUNT) ? args.getInt(OPTION_MAX_FILES_COUNT) : Extra.DEFAULT_FILES_COUNT;
			long maxFileSize = args.hasKey(OPTION_MAX_FILE_SIZE) ? (long) args.getDouble(OPTION_MAX_FILE_SIZE) : Extra.DEFAULT_FILE_SIZE;
			ReadableMap translations = args.hasKey(OPTION_TRANSLATIONS) ? args.getMap(OPTION_TRANSLATIONS) : null;
			HashMap<TranslationKey, String> convertedTranslations = new HashMap<>();

			if (translations != null) {
				for (ReadableMapKeySetIterator it = translations.keySetIterator(); it.hasNextKey();) {
					String key = it.nextKey();
					convertedTranslations.put(TranslationKey.valueOf(key), translations.getString(key));
				}
			}

			FilePicker
				.with(currentActivity)
				.maxCount(maxFilesCount)
				.maxFileSize(maxFileSize)
			 	.translations(convertedTranslations)
				.allowMultiple(multiple)
				.startActivityForResult(READ_REQUEST_CODE);

		} catch (ActivityNotFoundException e) {
			this.promise.reject(E_UNABLE_TO_OPEN_FILE_TYPE, e.getLocalizedMessage());
			this.promise = null;
		} catch (Exception e) {
			e.printStackTrace();
			this.promise.reject(E_FAILED_TO_SHOW_PICKER, e.getLocalizedMessage());
			this.promise = null;
		}
	}

	public void onShowActivityResult(int resultCode, Intent data, Promise promise) {
		if (resultCode == Activity.RESULT_CANCELED) {
			promise.reject(E_DOCUMENT_PICKER_CANCELED, "User canceled document picker");
		} else if (resultCode == Activity.RESULT_OK) {
			Uri uri = null;
			ClipData clipData = null;

			if (data != null) {
				uri = data.getData();
				clipData = data.getClipData();
			}

			try {
				WritableArray results = Arguments.createArray();
				if (data != null && data.hasExtra(Extra.DATA)) {
					File[] array = new Gson().fromJson(data.getStringExtra(Extra.DATA), File[].class);
					for (int i = 0; i < array.length; i++) {
						results.pushMap(getMetadata(array[i]));
					}
				} else {
					promise.reject(E_INVALID_DATA_RETURNED, "Invalid data returned by intent");
					return;
				}
				promise.resolve(results);
			} catch (Exception e) {
				promise.reject(E_UNEXPECTED_EXCEPTION, e.getLocalizedMessage(), e);
			}
		} else {
			promise.reject(E_UNKNOWN_ACTIVITY_RESULT, "Unknown activity result: " + resultCode);
		}
	}

	private WritableMap getMetadata(File file) {
		WritableMap map = Arguments.createMap();

		String uri = "file://" + file.getUrl();
		map.putString(FIELD_URI, uri);
		map.putString(FIELD_TYPE, getMimeType(getCurrentActivity(), Uri.parse(uri)));
		map.putString(FIELD_NAME, file.getName());
		map.putDouble(FIELD_SIZE, file.getSize());
		return map;
	}

	public String getMimeType(Context context, Uri uri) {
		String mimeType = null;
		if (uri.getScheme() != null && uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
			ContentResolver cr = context.getContentResolver();
			mimeType = cr.getType(uri);
		} else {
			String fileExtension = getExtension(uri.toString());
			mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
							fileExtension.toLowerCase());
		}
		return mimeType;
	}

	public String getExtension(String uri) {
		if (uri == null) {
			return null;
		}

		int dot = uri.lastIndexOf(".");
		if (dot >= 0) {
			return uri.substring(dot).replace(".", "");
		} else {
			// No extension.
			return "";
		}
	}
}
