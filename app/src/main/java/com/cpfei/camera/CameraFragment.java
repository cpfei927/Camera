/*
 * Copyright (c) 2014. Universal Pixels All Rights Reserved.
 */

package com.cpfei.camera;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.Area;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.AbsoluteLayout.LayoutParams;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("deprecation")
@SuppressLint("HandlerLeak")
public class CameraFragment extends Fragment implements OnClickListener,
		PictureCallback, Camera.ShutterCallback, OnSeekBarChangeListener,
		PreviewCallback, SurfaceTextureListener {
	private static String TAG = CameraFragment.class.getName();
	private static CameraFragment mInstance = null;
	// This handles everything about focus.
	private Context mContext;
	public Point mPoint;
	float sensorX = 0;
	float sensorY = 0;
	float sensorZ = 0;
	private TextureView mSurfaceView;
	private Camera mCamera;
	private boolean mPreviewRunning;
	private int mHeight;
	private int mWidth;
	private ViewGroup mLayout;
	private boolean isFocusing = false;
	private boolean isShootPressed = false;
	private AutoFocusCallback mAutoFocusCallBack = new AutoFocusCallback() {
		@Override
		public void onAutoFocus(boolean success, Camera camera) {
			if (isShootPressed) {
				isShootPressed = false;
				takePicture();
			}
			mFocusView.setVisibility(View.GONE);
			isFocusing = false;
		}
	};
	private PreviewCallback mPrevCallback = this;
	private boolean isSurfaceCreated = false;
	private int mDisplayOrientation = 90;
	private Matrix mMatrix = null;
	private int numberOfCameras;
	private int currentCameraId;
	// The first rear facing camera
	private int backCameraId = -1;
	private int frontCameraId = -1;
	private Size mPreviewSize;
	private ImageView mSwitchFlash;
	private ImageView mSwitchCamera;
	private ImageView mFocusView;
	private ImageView mBackView;
	private View mControlLayout;
	private View mSeekBarLayout;
	private SeekBar mSeekBar;
	private boolean isShowControlLayout = false;
	private boolean isFromExperience = false;
	private boolean hasAutoMode = false;
	private boolean isCloseCameraOnResume = false;
	private SensorManager sensorManager;
	private Sensor sensor;
	private SensorListener listener;
	private List<String> mFlashModes;
	private String mCurFlashMode;
	private int mRotation = 0;
	private GestureDetector mGestureDetector;
	private CameraListener mCameraListener;
	private View mView;

	private Handler mHandler = new Handler();
	private Runnable runnable = new Runnable() {
		@Override
		public void run() {
			mHandler.removeCallbacks(this);
			autoFocus();
			mHandler.postDelayed(runnable, 5000);
		}
	};
	private Handler mHandler2 = new Handler();

	public static CameraFragment getInstance() {
		return mInstance;
	}

	private Runnable runnable2 = new Runnable() {
		@Override
		public void run() {
			mHandler2.removeCallbacks(this);
			if (isSurfaceCreated) {
				openCamera();
			} else {
				mHandler2.postDelayed(runnable2, 1000);
			}
		}
	};

	public void setPreviewCallback(PreviewCallback callback) {
		mPrevCallback = callback;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mContext = this.getActivity();
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
		mView = inflater.inflate(R.layout.camera, container, false);
		findView();
		return mView;
	}

	protected void findView() {
		mGestureDetector = new GestureDetector(mContext,
				new DefaultGestureDetector());
		sensorManager = (SensorManager) mContext
				.getSystemService(Service.SENSOR_SERVICE);
		sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		listener = new SensorListener();
		mLayout = (ViewGroup) mView.findViewById(R.id.previewLayout);
		mControlLayout = mView.findViewById(R.id.controlLayout);
		mSeekBarLayout = mView.findViewById(R.id.seekBarLayout);
		mSeekBar = (SeekBar) mView.findViewById(R.id.seekBar);
		mSeekBar.setOnSeekBarChangeListener(this);

		ViewUtils.addVirtualKeyPadding(mContext, mControlLayout);
		updateControlLayout();
		
		mSurfaceView = new TextureView(mContext);
		mSurfaceView.setSurfaceTextureListener(this);
		mLayout.addView(mSurfaceView);

		// Find the total number of cameras available
		numberOfCameras = Camera.getNumberOfCameras();

		// Find the ID of the default camera
		CameraInfo cameraInfo = new CameraInfo();
		for (int i = 0; i < numberOfCameras; i++) {
			Camera.getCameraInfo(i, cameraInfo);
			if (cameraInfo.facing == CameraInfo.CAMERA_FACING_BACK) {
				backCameraId = i;
				currentCameraId = backCameraId;
			} else if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
				frontCameraId = i;
			}
		}

		mBackView = (ImageView) mView.findViewById(R.id.cameraImgBack);
		mBackView.setOnClickListener(this);
		mFocusView = (ImageView) mView.findViewById(R.id.focusImage);
		mSwitchCamera = (ImageView) mView.findViewById(R.id.switchCamera);
		mSwitchCamera.setOnClickListener(this);
		mSwitchFlash = (ImageView) mView.findViewById(R.id.switchFlash);
		mSwitchFlash.setOnClickListener(this);
		// mLayout.setLongClickable(true);
		// mLayout.setOnTouchListener(new OnTouchListener() {
		// @Override
		// public boolean onTouch(View v, MotionEvent event) {
		// return mGestureDetector.onTouchEvent(event);
		// }
		// });
	}

	public void showControlLayout(boolean isShow) {
		this.isShowControlLayout = isShow;
		updateControlLayout();
	}

	private void updateControlLayout() {
		if (mControlLayout == null) {
			return;
		}
		if (isShowControlLayout) {
			mSeekBarLayout.setVisibility(View.VISIBLE);
			mControlLayout.setVisibility(View.VISIBLE);
			SharedPreferences sp = mContext.getSharedPreferences(
					Consts.SP_NAME, Context.MODE_PRIVATE);
			Editor editor = sp.edit();
			editor.putBoolean(Consts.SP_KEY_HELP2, false);
			editor.commit();
		} else {
			mSeekBarLayout.setVisibility(View.GONE);
			mControlLayout.setVisibility(View.GONE);
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		mInstance = this;

		if (mSurfaceView == null) {
			mSurfaceView = new TextureView(mContext);
			mSurfaceView.setSurfaceTextureListener(this);
			mLayout.addView(mSurfaceView);
		}

		if (!isCloseCameraOnResume) {
			mHandler2.postDelayed(runnable2, 1000);
		}

		sensorManager.registerListener(listener, sensor,
				SensorManager.SENSOR_DELAY_UI);

		if (numberOfCameras == 1) {
			mSwitchCamera.setVisibility(View.GONE);
		}

		if (mCameraListener != null) {
			mCameraListener.onCameraResumed();
		}
	}

	public void setCloseCameraOnResume(boolean isClose) {
		isCloseCameraOnResume = isClose;
	}

	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
//		if (mPrevCallback != null) {
//			mPrevCallback.onPreviewFrame(data, camera);
//		}
	}

	private void rotateViews(int rotation) {
		Animation anim = new RotateAnimation(mRotation, rotation,
				Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
				0.5f);
		anim.setDuration(200);
		anim.setFillAfter(true);
		mBackView.startAnimation(anim);
		mSwitchCamera.startAnimation(anim);
		if (mSwitchFlash.getVisibility() == View.VISIBLE) {
			mSwitchFlash.startAnimation(anim);
		}
		mRotation = rotation;
	}

	@Override
	public void onPause() {
		sensorManager.unregisterListener(listener);
		closeCamera();
		
		if (mSurfaceView != null) {
			mLayout.removeView(mSurfaceView);
			mSurfaceView = null;
		}

		mHandler.removeCallbacks(runnable);
		mHandler2.removeCallbacks(runnable2);
		mInstance = null;
		super.onPause();
	}

	public void openCamera() {
		if (!isSurfaceCreated) {
			Log.e(TAG, "surface is still not created!");
		}

		if (mCamera != null) {
			return;
		}
		try {
			mCamera = Camera.open(currentCameraId);
			Parameters params = mCamera.getParameters();
			params.setPictureFormat(ImageFormat.JPEG);
			params.setPreviewFormat(ImageFormat.NV21);
			if (params.isZoomSupported()) {
				mSeekBar.setVisibility(View.VISIBLE);
				mSeekBar.setMax(params.getMaxZoom());
				mSeekBar.setProgress(params.getZoom());
			}
			mHeight = mLayout.getHeight();
			mWidth = mLayout.getWidth();
			mMatrix = new Matrix();
			Matrix matrix = new Matrix();
			prepareMatrix(matrix, false, mDisplayOrientation, mWidth, mHeight);
			// In face detection, the matrix converts the driver coordinates to
			// UI coordinates. In tap focus, the inverted matrix converts the UI
			// coordinates to driver coordinates.
			matrix.invert(mMatrix);
			List<Size> sizes = params.getSupportedPictureSizes();
			Size optimalSize = null;
			optimalSize = getOptimalPicSize(sizes, Consts.CAP_IMAGE_WIDTH,
					Consts.CAP_IMAGE_HEIGHT);
			params.setPictureSize(optimalSize.width, optimalSize.height);
			Log.d(TAG, "picture size: " + optimalSize.width + "x"
					+ optimalSize.height);
			sizes = params.getSupportedPreviewSizes();
			optimalSize = getOptimalSize(sizes, mWidth, mHeight);
			params.setPreviewSize(optimalSize.width, optimalSize.height);
			// params.setRotation(90);
			int opWidth = optimalSize.height;
			int opHeight = optimalSize.width;
			Log.d(TAG, "preview size: " + optimalSize.width + "x"
					+ optimalSize.height);
			mPreviewSize = optimalSize;

			double layoutRatio = 1.0f * mWidth / mHeight;
			double opRatio = 1.0f * opWidth / opHeight;
			if (layoutRatio > opRatio) {
				opHeight = (int) (opHeight * 1.0f * mWidth / opWidth);
				opWidth = mWidth;
			} else {
				opWidth = (int) (opWidth * 1.0f * mHeight / opHeight);
				opHeight = mHeight;
			}

			LayoutParams lp = (LayoutParams) mSurfaceView.getLayoutParams();
			lp.width = opWidth;
			lp.height = opHeight;
			lp.x = (mWidth - opWidth) / 2;
			lp.y = (mHeight - opHeight) / 2;

			Log.d(TAG, "surface position: " + lp.x + "," + lp.y);
			Log.d(TAG, "surface size: " + lp.width + "," + lp.height);
			mSurfaceView.setLayoutParams(lp);

			List<String> modes = params.getSupportedFocusModes();
			@SuppressWarnings("unused")
			boolean isContinuous = false;
			hasAutoMode = false;
			for (String mode : modes) {
				if (mode.equalsIgnoreCase(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
					isContinuous = true;
				} else if (mode.equalsIgnoreCase(Parameters.FOCUS_MODE_AUTO)) {
					hasAutoMode = true;
				}
			}

			// if (isContinuous) {
			// params.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
			// } else
			if (hasAutoMode) {
				params.setFocusMode(Parameters.FOCUS_MODE_AUTO);
				mHandler.postDelayed(runnable, 5000);
			}

			CameraInfo info = new CameraInfo();
			Camera.getCameraInfo(currentCameraId, info);
			if (info.facing == CameraInfo.CAMERA_FACING_BACK) {
				mFlashModes = params.getSupportedFlashModes();
				if (mFlashModes != null) {
					mSwitchFlash.setVisibility(View.VISIBLE);
					updateFlashIcon(mCurFlashMode);
					setFlashMode(Parameters.FLASH_MODE_OFF);
				} else {
					mSwitchFlash.setVisibility(View.GONE);
				}
			} else {
				mSwitchFlash.setVisibility(View.GONE);
			}
			mCamera.setParameters(params);
			mCamera.setDisplayOrientation(mDisplayOrientation);
			mCamera.setPreviewTexture(mSurfaceView.getSurfaceTexture());
			mCamera.startPreview();
			mCamera.setPreviewCallback(this);

			mPreviewRunning = true;
			isFocusing = false;
			isShootPressed = false;
		} catch (Exception e) {
			closeCamera();
			e.printStackTrace();
		}
	}

	private void setFlashMode(String mode) {
		if (mFlashModes == null) {
			return;
		}
		try {
			int cameraId = (currentCameraId) % numberOfCameras;
			CameraInfo info = new CameraInfo();
			Camera.getCameraInfo(cameraId, info);
			if (info.facing == CameraInfo.CAMERA_FACING_BACK) {
				if (mFlashModes.indexOf(mCurFlashMode) >= 0) {
					Parameters params = mCamera.getParameters();
					params.setFlashMode(mode);
					mCamera.setParameters(params);
				}
			}
		} catch (Exception exception) {
			Log.e(TAG, "IOException caused by setFlashMode", exception);
		}

	}

	private void updateFlashIcon(String mode) {
		if (mFlashModes == null) {
			return;
		}
		mCurFlashMode = mode;
		if (TextUtils.isEmpty(mCurFlashMode)
				|| mFlashModes.indexOf(mCurFlashMode) < 0) {
			switchFlashMode();
		} else {
			if (mCurFlashMode.equalsIgnoreCase(Parameters.FLASH_MODE_OFF)) {
				mSwitchFlash.setImageResource(R.mipmap.ic_launcher);
			} else if (mCurFlashMode
					.equalsIgnoreCase(Parameters.FLASH_MODE_AUTO)) {
				// mSwitchFlash.setImageResource(R.drawable.ic_flash_auto);
			} else {
				mSwitchFlash.setImageResource(R.mipmap.ic_launcher);
			}
		}
	}

	private void switchFlashMode() {
		if (TextUtils.isEmpty(mCurFlashMode)) {
			updateFlashIcon(Parameters.FLASH_MODE_OFF);
		} else if (mCurFlashMode.equalsIgnoreCase(Parameters.FLASH_MODE_OFF)) {
			updateFlashIcon(Parameters.FLASH_MODE_AUTO);
		} else if (mCurFlashMode.equalsIgnoreCase(Parameters.FLASH_MODE_AUTO)) {
			updateFlashIcon(Parameters.FLASH_MODE_ON);
		} else {
			updateFlashIcon(Parameters.FLASH_MODE_OFF);
		}
	}

	public boolean setFocusArea(int x, int y) {
		return setFocusArea(x, y, false);
	}

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	public boolean setFocusArea(int x, int y, boolean isFocusImmediate) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH
				&& mCamera != null) {
			int s = DensityUtils.dip2px(mContext, 100);
			List<Area> focusAreas = new ArrayList<Area>();
			Area area = generateArea(x, y, s);
			focusAreas.add(area);

			s = DensityUtils.dip2px(mContext, 150);
			List<Area> MeteringAreas = new ArrayList<Area>();
			Area meterArea = generateArea(x, y, s);
			MeteringAreas.add(meterArea);
			Parameters parameters = mCamera.getParameters();
			int nFocusAreas = parameters.getMaxNumFocusAreas();
			int nMeteringAreas = parameters.getMaxNumMeteringAreas();
			if (nFocusAreas > 0) {
				mCamera.cancelAutoFocus();
				parameters.setFocusAreas(focusAreas);
				if (nMeteringAreas > 0) {
					parameters.setMeteringAreas(MeteringAreas);
				}
				mCamera.setParameters(parameters);
				if (isFocusImmediate) {
					isShootPressed = true;
					mCamera.autoFocus(mAutoFocusCallBack);
				}
				return true;
			}
		}
		return false;
	}

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private Area generateArea(int x, int y, int s) {
		x -= s / 2;
		y -= s / 2;
		x = clamp(x, 0, mWidth - s);
		y = clamp(y, 0, mHeight - s);
		Rect rect = new Rect();
		RectF rectF = new RectF(x, y, x + s, y + s);
		mMatrix.mapRect(rectF);
		rectFToRect(rectF, rect);
		Area area = new Area(rect, 1);
		return area;
	}

	private int clamp(int x, int min, int max) {
		if (x > max) {
			return max;
		}
		if (x < min) {
			return min;
		}
		return x;
	}

	private void prepareMatrix(Matrix matrix, boolean mirror,
			int displayOrientation, int viewWidth, int viewHeight) {
		// Need mirror for front camera.
		matrix.setScale(mirror ? -1 : 1, 1);
		// This is the value for android.hardware.Camera.setDisplayOrientation.
		matrix.postRotate(displayOrientation);
		// Camera driver coordinates range from (-1000, -1000) to (1000, 1000).
		// UI coordinates range from (0, 0) to (width, height).
		matrix.postScale(viewWidth / 2000f, viewHeight / 2000f);
		matrix.postTranslate(viewWidth / 2f, viewHeight / 2f);
	}

	private void rectFToRect(RectF rectF, Rect rect) {
		rect.left = Math.round(rectF.left);
		rect.top = Math.round(rectF.top);
		rect.right = Math.round(rectF.right);
		rect.bottom = Math.round(rectF.bottom);
	}

	public int getPreviewWidth() {
		if (mPreviewSize != null) {
			return mPreviewSize.width;
		} else {
			return -1;
		}
	}

	public int getPreviewHeight() {
		if (mPreviewSize != null) {
			return mPreviewSize.height;
		} else {
			return -1;
		}
	}

	public void closeCamera() {
		if (mCamera != null) {
			mCamera.setPreviewCallback(null);
			pausePreview();
			mHandler.removeCallbacks(runnable);
			mCamera.release();
			mCamera = null;
		}
	}

	public void pausePreview() {
		if (mPreviewRunning && mCamera != null) {
			mCamera.stopPreview();
			mPreviewRunning = false;
		}
	}

	public void resumePreview() {
		if (!mPreviewRunning && mCamera != null) {
			mCamera.startPreview();
			mPreviewRunning = true;
		}
	}

	private Size getOptimalPicSize(List<Size> sizes, int width, int height) {
		final double ASPECT_TOLERANCE = 0.3;
		if (sizes == null) {
			return null;
		}
		Size optimalSize = null;
		int targetHeight = width;
		int targetWidth = height;
		double minDiff = Double.MAX_VALUE;
		double targetRatio = 1.0f * height / width;
		for (Size size : sizes) {
			double ratio = (double) size.width / size.height;
			if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) {
				continue;
			}
			if (size.height > targetHeight && size.width > targetWidth
					&& Math.abs(size.height - targetHeight) < minDiff) {
				optimalSize = size;
				minDiff = Math.abs(size.height - targetHeight);
			}
		}

		// Cannot find the one match the aspect ratio. This should not happen.
		// Ignore the requirement.
		if (optimalSize == null) {
			optimalSize = getOptimalSize(sizes, width, height);
		}
		return optimalSize;
	}

	private Size getOptimalSize(List<Size> sizes, int width, int height) {
		// Use a very small tolerance because we want an exact match.
		final double ASPECT_TOLERANCE = 0.1;
		if (sizes == null) {
			return null;
		}
		Size optimalSize = null;
		double minDiff = Double.MAX_VALUE;
		int targetHeight = width;
		double targetRatio = 1.0f * height / width;
		for (Size size : sizes) {
			double ratio = (double) size.width / size.height;
			if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) {
				continue;
			}
			if (Math.abs(size.height - targetHeight) < minDiff) {
				optimalSize = size;
				minDiff = Math.abs(size.height - targetHeight);
			}
		}

		// Cannot find the one match the aspect ratio. This should not happen.
		// Ignore the requirement.
		if (optimalSize == null) {
			minDiff = Double.MAX_VALUE;
			for (Size size : sizes) {
				if (Math.abs(size.height - targetHeight) <= minDiff) {
					optimalSize = size;
					minDiff = Math.abs(size.height - targetHeight);
				}
			}
		}
		return optimalSize;
	}

	@Override
	public void onSurfaceTextureAvailable(SurfaceTexture surface, int width,
			int height) {
		Log.i(TAG, "surface texture is available");
		isSurfaceCreated = true;
		try {
			if (mCamera != null) {
				mCamera.setPreviewTexture(mSurfaceView.getSurfaceTexture());
			}
		} catch (IOException exception) {
			Log.e(TAG, "IOException caused by setPreviewTexture()",
					exception);
		}
	}

	@Override
	public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width,
			int height) {
		Log.i(TAG, "surface texture changed");
		if (mCamera != null) {
			Parameters parameters = mCamera.getParameters();
			parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);

			mCamera.setParameters(parameters);
			mCamera.startPreview();
		}
	}

	@Override
	public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
		Log.i(TAG, "surface texture destroyed");
		isSurfaceCreated = false;
		closeCamera();
		return false;
	}

	@Override
	public void onSurfaceTextureUpdated(SurfaceTexture surface) {

	}

	public boolean autoFocus() {
		try {
			if (mCamera != null && mPreviewRunning) {
				if (!isFocusing) {
					mCamera.autoFocus(mAutoFocusCallBack);
					isFocusing = true;
				}
			}
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public void takePicture() {
		try {
			new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					if (mCamera != null) {
						mCamera.takePicture(CameraFragment.this, null,
								CameraFragment.this);
					}
				}
			}, 100);
			mPreviewRunning = false;
			isShootPressed = false;
			// if (autoFocus()) {
			// isShootPressed = true;
			// }
			// }
		} catch (Exception e) {
			e.printStackTrace();
			isShootPressed = false;
		} catch (Error error) {
			error.printStackTrace();
			isShootPressed = false;
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.switchCamera:
			// check for availability of multiple cameras
			if (numberOfCameras == 1) {
				AlertDialog.Builder builder = new AlertDialog.Builder(
						this.getActivity());
				builder.setMessage("没有前置摄像头").setNeutralButton("Close", null);
				AlertDialog alert = builder.create();
				alert.show();
			} else {
				closeCamera();

				if (currentCameraId == backCameraId) {
					currentCameraId = frontCameraId;
				} else {
					currentCameraId = backCameraId;
				}
				openCamera();
			}
			break;
		case R.id.switchFlash:
			switchFlashMode();
			break;
		case R.id.cameraImgBack:
			if (isFromExperience) {
				getActivity().finish();
			} else {
				getActivity().finish();
			}
			break;
		default:
			break;
		}
	}

	@Override
	public void onPictureTaken(byte[] data, Camera camera) {
		isShootPressed = false;
		Bitmap bm = cropImage(Consts.CAP_IMAGE_HEIGHT, Consts.CAP_IMAGE_WIDTH,
				data);
		if (bm != null) {
//			String name = String.valueOf(System.currentTimeMillis()) + ".jpg";
//			String url = FileUtils.generateFileUrl(mContext, name, "post");
//			// FileUtils.generateFileUrl(mContext, name);
//			boolean bSaved = PictureUtils.savePicture(mContext, bm, url,
//					CompressFormat.JPEG, 90);
//			bm.recycle();
//			bm = null;
		} else {
			resumePreview();
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == Activity.RESULT_OK && requestCode == 0) {
			showControlLayout(false);
		}
	}

	private Point processPoint(Bitmap bm) {
		System.out.println("pointX  :" + mPoint.getX());
		System.out.println("pointY  :" + mPoint.getY());
		Point point = new Point();
		int viewW = mLayout.getMeasuredWidth();
		int viewH = mLayout.getMeasuredHeight();
		int imageW;
		int imageH;
		if (mRotation != 0) {
			imageW = bm.getHeight();
			imageH = bm.getWidth();
		} else {
			imageW = bm.getWidth();
			imageH = bm.getHeight();
		}

		float imageRatio = imageW * 1.0f / imageH;
		float viewRatio = viewW * 1.0f / viewH;
		float scale = 1.0f;
		int offsetX = 0;
		int offsetY = 0;
		if (imageRatio > viewRatio) {
			scale = viewH * 1.0f / imageH;
			offsetX = (int) ((imageW * scale - viewW) / 2);
		} else {
			scale = viewW * 1.0f / imageW;
			offsetY = (int) ((imageH * scale - viewH) / 2);
		}

		int pointX = (int) ((mPoint.getX() + offsetX) / scale);
		int pointY = (int) ((mPoint.getY() + offsetY) / scale);
		System.out.println("pointX:" + pointX);
		System.out.println("pointY:" + pointY);
		if (mRotation == 0) {
			point.setX(pointX);
			point.setY(pointY);
		} else if (mRotation == 90) {
			point.setX(pointY);
			point.setY(imageW - pointX);
		} else {
			point.setX(imageH - pointY);
			point.setY(pointX);
		}

		System.out.println("pointX  :" + point.getX());
		System.out.println("pointY  :" + point.getY());
		return point;
	}

	@Override
	public void onShutter() {
		// SoundManager.getInstance().playSound(R.raw.take_picture);
	}

	private Bitmap cropImage(int reqWidth, int reqHeight, byte[] data) {
		try {
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inJustDecodeBounds = true;
			BitmapFactory.decodeByteArray(data, 0, data.length, options);
			int height = options.outHeight;
			int width = options.outWidth;
			int inSampleSize = 1;
			float hRatio = 1;
			float wRatio = 1;
			if (height > reqHeight && width > reqWidth) {
				hRatio = (float) height / (float) reqHeight;
				wRatio = (float) width / (float) reqWidth;
				inSampleSize = Math.round(Math.min(hRatio, wRatio) - 0.5f);
			}
			inSampleSize = inSampleSize > 1 ? inSampleSize : 1;

			options.inJustDecodeBounds = false;
			options.inSampleSize = inSampleSize;
			options.inPreferredConfig = Bitmap.Config.RGB_565;
			Bitmap bm = BitmapFactory.decodeByteArray(data, 0, data.length,
					options);
			Matrix m = new Matrix();
			width = bm.getWidth();
			height = bm.getHeight();
			int rotation = 0;
			if (currentCameraId == frontCameraId && mRotation == 0) {
				rotation = -90;
			} else {
				rotation = 90 - mRotation;
			}
			m.setRotate(rotation, (float) width / 2, (float) height / 2);
			float scale = 1;
			int xOffset = 0;
			int yOffset = 0;
			int wSize = width;
			int hSize = height;
			if (width >= reqWidth && height >= reqHeight) {
				hRatio = (float) height / (float) reqHeight;
				wRatio = (float) width / (float) reqWidth;
				if (hRatio > wRatio) {
					scale = (float) reqWidth / (float) width;
					hSize = (int) (reqHeight * wRatio);
					yOffset = (height - hSize) / 2;
				} else {
					scale = (float) reqHeight / (float) height;
					wSize = (int) (reqWidth * hRatio);
					xOffset = (width - wSize) / 2;
				}
				m.postScale(scale, scale);
			}

			Bitmap b2 = Bitmap.createBitmap(bm, xOffset, yOffset, wSize, hSize,
					m, true);
			if (bm != b2) {
				bm.recycle();
				bm = b2;
			}
			return bm;
		} catch (OutOfMemoryError error) {
			error.printStackTrace();
			return null;
		}
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress,
			boolean fromUser) {
		if (mCamera != null) {
			Parameters parameters = mCamera.getParameters();
			mSeekBar.setProgress(progress);
			parameters.setZoom(progress);
			mCamera.setParameters(parameters);
		}
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {

	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {

	}

	class SensorListener implements SensorEventListener {

		@Override
		public void onSensorChanged(SensorEvent event) {
			if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
				sensorX = event.values[SensorManager.DATA_X];
				sensorY = event.values[SensorManager.DATA_Y];
				sensorZ = event.values[SensorManager.DATA_Z];
				int rotation = 0;
				if (mControlLayout != null && mControlLayout.getVisibility() == View.VISIBLE) {
					if (sensorX > 4.5) {
						rotation = 90;
					} else if (sensorX < -4.5) {
						rotation = -90;
					}

					// UPDirectionUtils.getInstance().setRotation(mRotation);
					// Log.wtf("!!!!!!!!", "  x=" + sensorX + "  y=" + sensorY
					// + " z=" + sensorZ);
				}
				if (mRotation != rotation) {
					rotateViews(rotation);
				}
			}
		}

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {

		}
	}

	class DefaultGestureDetector extends SimpleOnGestureListener {

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
				float velocityY) {
			return false;
		}

		@Override
		public boolean onSingleTapUp(MotionEvent e) {
			mPoint = new Point();
			mPoint.setX((int) e.getX());
			mPoint.setY((int) e.getY());

			Log.d("===============", "===============" + e.getX());

			if (!isShootPressed) {
				setFlashMode(mCurFlashMode);
				new Handler().postDelayed(new Runnable() {
					@Override
					public void run() {
						if (!hasAutoMode) {
							takePicture();
						} else {
							if (setFocusArea(mPoint.getX(), mPoint.getY(), true)) {
								mFocusView.setVisibility(View.VISIBLE);
								LayoutParams params = (LayoutParams) mFocusView
										.getLayoutParams();
								params.x = mPoint.getX() - params.width / 2;
								params.y = mPoint.getY() - params.height / 2;
								mFocusView.setLayoutParams(params);
							} else {
								takePicture();
							}
						}
					}
				}, 300);
			}
			return true;
		}

		@Override
		public void onLongPress(MotionEvent e) {
		}
	}

	public void setCameraListener(CameraListener l) {
		mCameraListener = l;
	}

	public interface CameraListener {
		public void onCameraResumed();
	}

}
