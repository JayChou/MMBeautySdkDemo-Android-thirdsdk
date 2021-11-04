package com.qiniu.droid.rtc.demo.activity;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.PopupWindow;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.cosmos.thirdlive.QiniuBeautyManager;
import com.cosmos.thirdlive.QiniuRtcManager;
import com.qiniu.droid.rtc.QNBeautySetting;
import com.qiniu.droid.rtc.QNCameraSwitchResultCallback;
import com.qiniu.droid.rtc.QNCaptureVideoCallback;
import com.qiniu.droid.rtc.QNCustomMessage;
import com.qiniu.droid.rtc.QNErrorCode;
import com.qiniu.droid.rtc.QNLocalAudioPacketCallback;
import com.qiniu.droid.rtc.QNRTCEngine;
import com.qiniu.droid.rtc.QNRTCEngineEventListener;
import com.qiniu.droid.rtc.QNRTCSetting;
import com.qiniu.droid.rtc.QNRemoteAudioPacketCallback;
import com.qiniu.droid.rtc.QNRoomState;
import com.qiniu.droid.rtc.QNSourceType;
import com.qiniu.droid.rtc.QNStatisticsReport;
import com.qiniu.droid.rtc.QNTrackInfo;
import com.qiniu.droid.rtc.QNTrackKind;
import com.qiniu.droid.rtc.QNVideoFormat;
import com.qiniu.droid.rtc.demo.R;
import com.qiniu.droid.rtc.demo.fragment.ControlFragment;
import com.qiniu.droid.rtc.demo.model.RTCRoomUsersMergeOption;
import com.qiniu.droid.rtc.demo.model.RTCTrackMergeOption;
import com.qiniu.droid.rtc.demo.model.RTCUserMergeOptions;
import com.qiniu.droid.rtc.demo.service.ForegroundService;
import com.qiniu.droid.rtc.demo.ui.CircleTextView;
import com.qiniu.droid.rtc.demo.ui.MergeLayoutConfigView;
import com.qiniu.droid.rtc.demo.ui.UserTrackView;
import com.qiniu.droid.rtc.demo.utils.Config;
import com.qiniu.droid.rtc.demo.utils.QNAppServer;
import com.qiniu.droid.rtc.demo.utils.SplitUtils;
import com.qiniu.droid.rtc.demo.utils.ToastUtils;
import com.qiniu.droid.rtc.demo.utils.TrackWindowMgr;
import com.qiniu.droid.rtc.demo.utils.Utils;
import com.qiniu.droid.rtc.model.QNAudioDevice;
import com.qiniu.droid.rtc.model.QNForwardJob;
import com.qiniu.droid.rtc.model.QNMergeJob;
import com.qiniu.droid.rtc.model.QNMergeTrackOption;

import org.webrtc.Size;
import org.webrtc.VideoFrame;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static com.qiniu.droid.rtc.demo.utils.Config.DEFAULT_BITRATE;
import static com.qiniu.droid.rtc.demo.utils.Config.DEFAULT_FPS;
import static com.qiniu.droid.rtc.demo.utils.Config.DEFAULT_RESOLUTION;

public class RoomActivity extends Activity implements QNRTCEngineEventListener, ControlFragment.OnCallEvents {
    private static final String TAG = "RoomActivity";
    private static final int BITRATE_FOR_SCREEN_VIDEO = (int) (1.5 * 1000 * 1000);

    public static final String EXTRA_USER_ID = "USER_ID";
    public static final String EXTRA_ROOM_TOKEN = "ROOM_TOKEN";
    public static final String EXTRA_ROOM_ID = "ROOM_ID";

    private static final int JOB_STOP_DELAY_TIME = 5000;

    private static final String[] MANDATORY_PERMISSIONS = {
            "android.permission.MODIFY_AUDIO_SETTINGS",
            "android.permission.RECORD_AUDIO",
            "android.permission.INTERNET"
    };

    private Handler mHandler;

    private Toast mLogToast;
    private List<String> mHWBlackList = new ArrayList<>();

    private UserTrackView mTrackWindowFullScreen;
    private List<UserTrackView> mTrackWindowsList;
    private AlertDialog mKickOutDialog;

    private QNRTCEngine mEngine;
    private String mRoomToken;
    private String mUserId;
    private String mRoomId;
    private boolean mMicEnabled = true;
    private boolean mBeautyEnabled = false;
    private boolean mVideoEnabled = true;
    private boolean mSpeakerEnabled = true;
    private boolean mIsError = false;
    private boolean mIsAdmin = false;
    private boolean mIsJoinedRoom = false;
    private boolean mAddExtraAudioData = false;
    private boolean mEnableAudioEncrypt = false;
    private ControlFragment mControlFragment;
    private List<QNTrackInfo> mLocalTrackList;

    private QNTrackInfo mLocalVideoTrack;
    private QNTrackInfo mLocalAudioTrack;
    private QNTrackInfo mLocalScreenTrack;

    private int mScreenWidth = 0;
    private int mScreenHeight = 0;
    private int mCaptureMode = Config.CAMERA_CAPTURE;

    private TrackWindowMgr mTrackWindowMgr;
    private QiniuRtcManager qiniuRtcManager;
    private boolean frontCamera = true;

    /**
     * 合流相关
     *
     * 注意：
     * 一个房间仅需要一个用户可以配置合流布局即可，该用户可以基于 SDK 提供的远端用户相关回调对远端用户的动态进行监听，
     * 进而进行合流布局的实时更改。
     *
     * demo 中默认 userId 为 "admin" 的用户可以控制合流布局的配置
     */
    private MergeLayoutConfigView mMergeLayoutConfigView;
    private PopupWindow mPopWindow;
    private UserListAdapter mUserListAdapter;
    private RTCRoomUsersMergeOption mRoomUsersMergeOption;
    private RTCUserMergeOptions mChooseUser;
    private volatile boolean mIsMergeJobStreaming;
    /**
     * 如果 QNMergeJob 为 null，则表示使用默认合流任务
     *
     * 默认合流任务的宽高、码率等参数可以通过登录后台 https://portal.qiniu.com/rtn 并对连麦应用进行编辑来配置
     * 自定义合流任务可以通过自定义 {@link QNMergeJob} 并调用 QNRTCEngine.createMergeJob 接口来创建
     * 注意：创建自定义合流任务需要在加入房间之后才可执行
     */
    private QNMergeJob mCurrentMergeJob;

    /**
     * 单路转推相关
     *
     * 注意：
     * 1. 单路转推仅支持配置一路音频和一路视频
     * 2. 单路转推场景需要在初始化的时候保证配置了 "固定分辨率"{@link QNRTCSetting#setMaintainResolution} 选项的开启，否则会出问题！！！
     *
     * demo 中默认 userId 为 "admin" 的用户可以开启单路转推功能
     */
    private QNForwardJob mForwardJob;
    private volatile boolean mIsForwardJobStreaming;

    /**
     * 如果您的场景包括合流转推和单路转推的切换，那么务必维护一个 serialNum 的参数，代表流的优先级，
     * 使其不断自增来实现 rtmp 流的无缝切换，否则可能会出现抢流的现象
     *
     * QNMergeJob 以及 QNForwardJob 中 publishUrl 的格式为：rtmp://domain/app/stream?serialnum=xxx
     *
     * 切换流程推荐为：
     * 1. 单路转推 -> 创建合流任务（以创建成功的回调为准） -> 停止单路转推
     * 2. 合流转推 -> 创建单路转推任务（以创建成功的回调为准） -> 停止合流转推
     *
     * 注意：
     * 1. 两种合流任务，推流地址应该保持一致，只有 serialnum 存在差异
     * 2. 在两种推流任务切换的场景下，合流任务务必使用自定义合流任务，并指定推流地址的 serialnum
     */
    private int mSerialNum = 0;

    private Semaphore mCaptureStoppedSem = new Semaphore(1);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(getSystemUiVisibility());
        mHandler = new Handler();

        final WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics outMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(outMetrics);
        mScreenWidth = outMetrics.widthPixels;
        mScreenHeight = outMetrics.heightPixels;

        setContentView(R.layout.activity_muti_track_room);

        Intent intent = getIntent();
        mRoomToken = "IugmkdeOFeeybGUEKXIB7Mp0J7A3l0OY9IRd3EK-:6ZAWb-IKt90y3_bby9TvMJgLZyg=:eyJhcHBJZCI6ImUzbXFqdDd4eiIsInVzZXJJZCI6InAyMjR0MDNveHNzIiwicm9vbU5hbWUiOiJtZWV0X3AyMjR0MDNveHNzIiwicGVybWlzc2lvbiI6ImFkbWluIiwiZXhwaXJlQXQiOjE2MzU0NzcyOTJ9";
        mUserId = intent.getStringExtra(EXTRA_USER_ID);
        mRoomId = intent.getStringExtra(EXTRA_ROOM_ID);
        mIsAdmin = mUserId.equals(QNAppServer.ADMIN_USER);

        mTrackWindowFullScreen = (UserTrackView) findViewById(R.id.track_window_full_screen);
        mTrackWindowsList = new LinkedList<UserTrackView>(Arrays.asList(
                (UserTrackView) findViewById(R.id.track_window_a),
                (UserTrackView) findViewById(R.id.track_window_b),
                (UserTrackView) findViewById(R.id.track_window_c),
                (UserTrackView) findViewById(R.id.track_window_d),
                (UserTrackView) findViewById(R.id.track_window_e),
                (UserTrackView) findViewById(R.id.track_window_f),
                (UserTrackView) findViewById(R.id.track_window_g),
                (UserTrackView) findViewById(R.id.track_window_h),
                (UserTrackView) findViewById(R.id.track_window_i)
        ));

        for (final UserTrackView view : mTrackWindowsList) {
            view.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (mIsAdmin) {
                        showKickoutDialog(view.getUserId());
                    }
                    return false;
                }
            });
        }

        // 初始化控制面板
        mControlFragment = new ControlFragment();
        mControlFragment.setArguments(intent.getExtras());
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.add(R.id.control_fragment_container, mControlFragment);
        ft.commitAllowingStateLoss();

        // 权限申请
        for (String permission : MANDATORY_PERMISSIONS) {
            if (checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                logAndToast("Permission " + permission + " is not granted");
                setResult(RESULT_CANCELED);
                finish();
                return;
            }
        }

        // 初始化 rtcEngine 和本地 TrackInfo 列表
        initQNRTCEngine();
        // 初始化本地音视频 track
        initLocalTrackInfoList();
        // 初始化合流相关配置
        initMergeLayoutConfig();

        // 多人显示窗口管理类
        mTrackWindowMgr = new TrackWindowMgr(mUserId, mScreenWidth, mScreenHeight, outMetrics.density
                , mEngine, mTrackWindowFullScreen, mTrackWindowsList);

        List<QNTrackInfo> localTrackListExcludeScreenTrack = new ArrayList<>(mLocalTrackList);
        localTrackListExcludeScreenTrack.remove(mLocalScreenTrack);
        mTrackWindowMgr.addTrackInfo(mUserId, localTrackListExcludeScreenTrack);
        qiniuRtcManager = new QiniuRtcManager(RoomActivity.this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 开始视频采集
        if (mCaptureMode == Config.CAMERA_CAPTURE || mCaptureMode == Config.MUTI_TRACK_CAPTURE) {
            startCaptureAfterAcquire();
        }
        if (!mIsJoinedRoom) {
            // 加入房间
            mEngine.joinRoom(mRoomToken);
        }
    }

    private void startCaptureAfterAcquire() {
        boolean acquired = false;
        try {
            acquired = mCaptureStoppedSem.tryAcquire(2000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (acquired) {
            mEngine.startCapture();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 停止视频采集
        if (mCaptureMode == Config.CAMERA_CAPTURE || mCaptureMode == Config.MUTI_TRACK_CAPTURE) {
            mEngine.stopCapture();
        }
        if (mPopWindow != null && mPopWindow.isShowing()) {
            mPopWindow.dismiss();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mEngine != null) {
            if (mIsAdmin && mIsMergeJobStreaming) {
                // 如果当前正在合流，则停止
                mEngine.stopMergeStream(mCurrentMergeJob == null ? null : mCurrentMergeJob.getMergeJobId());
                mIsMergeJobStreaming = false;
            }
            if (mIsAdmin && mIsForwardJobStreaming) {
                mEngine.stopForwardJob(mForwardJob.getForwardJobId());
                mIsForwardJobStreaming = false;
            }
            // 释放相关资源
            mEngine.destroy();
            mEngine = null;
        }
        if (mTrackWindowFullScreen != null) {
            mTrackWindowFullScreen.dispose();
        }
        for (UserTrackView item : mTrackWindowsList) {
            item.dispose();
        }
        mTrackWindowsList.clear();
        mPopWindow = null;
    }

    /**
     * 初始化 QNRTCEngine
     */
    private void initQNRTCEngine() {
        SharedPreferences preferences = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
        int videoWidth = preferences.getInt(Config.WIDTH, DEFAULT_RESOLUTION[1][0]);
        int videoHeight = preferences.getInt(Config.HEIGHT, DEFAULT_RESOLUTION[1][1]);
        int fps = preferences.getInt(Config.FPS, DEFAULT_FPS[1]);
        boolean isHwCodec = preferences.getInt(Config.CODEC_MODE, Config.HW) == Config.HW;
        int videoBitrate = preferences.getInt(Config.BITRATE, DEFAULT_BITRATE[1]);
        /**
         * 默认情况下，网络波动时 SDK 内部会降低帧率或者分辨率来保证带宽变化下的视频质量；
         * 如果打开分辨率保持开关，则只会调整帧率来适应网络波动。
         */
        boolean isMaintainRes = preferences.getBoolean(Config.MAINTAIN_RES, false);

        /**
         * 如果您的使用场景需要双讲，建议按照默认设置，保持 QNRTCSetting#setLowAudioSampleRateEnabled
         * 和 QNRTCSetting#setAEC3Enabled 为 true 以防止出现对讲回声
         */
        boolean isLowSampleRateEnabled = preferences.getInt(Config.SAMPLE_RATE, Config.HIGH_SAMPLE_RATE) == Config.LOW_SAMPLE_RATE;
        boolean isAec3Enabled = preferences.getBoolean(Config.AEC3_ENABLE, true);
        mCaptureMode = preferences.getInt(Config.CAPTURE_MODE, Config.CAMERA_CAPTURE);

        /**
         * VideoPreviewFormat 和 VideoEncodeFormat 建议保持一致
         */
        QNVideoFormat format = new QNVideoFormat(videoWidth, videoHeight, fps);
        QNRTCSetting setting = new QNRTCSetting();
        setting.setCameraID(QNRTCSetting.CAMERA_FACING_ID.FRONT)
                .setHWCodecEnabled(isHwCodec)
                .setMaintainResolution(isMaintainRes)
                .setVideoBitrate(videoBitrate)
                .setLowAudioSampleRateEnabled(isLowSampleRateEnabled)
                .setAEC3Enabled(isAec3Enabled)
                .setVideoEncodeFormat(format)
                .setVideoPreviewFormat(format);
        mEngine = QNRTCEngine.createEngine(getApplicationContext(), setting, this);
        mEngine.setCaptureVideoCallBack(new QNCaptureVideoCallback() {

            @Override
            public int[] onCaptureOpened(List<Size> sizes, List<Integer> fpsAscending) {
                // 根据设备能力选择匹配的采集参数
                int wantSize = -1; // 选择的分辨率下标, -1 表示不做选择, 使用 QNRTCSetting 的设置
                int wantFps = -1;  // 选择的帧率下标, -1 表示不做选择, 使用 QNRTCSetting 的设置
                /**
                 * 以下代码仅示例：
                 * 当硬件可用分辨率和当前设置宽高一致时，直接选择该分辨率；当没有完全一致的宽高时，选择使用高度匹配的一个分辨率;
                 * 您也可以根据自己业务需要，选择宽高最接近的一个分辨率或其他匹配方式。
                 *
                 * 如果没有需要，也可以返回 -1 由 SDK 根据设置来匹配接近的分辨率。
                 */
                for (int i = 0; i < sizes.size(); i++) {
                   if (sizes.get(i).height == videoHeight) {
                       wantSize = i;
                       if (sizes.get(i).width == videoWidth) {
                            break;
                       }
                   }
                }
                return new int[] {wantSize, wantFps};
            }

            @Override
            public void onCaptureStarted() {
            }

            private byte[] data;
            private int width;
            private int height;
            private int rotation;
            @Override
            public void onRenderingFrame(VideoFrame.TextureBuffer texBuf, long timestampNs) {
                if (data == null || qiniuRtcManager == null) {
                    return;
                }
                int beautyTexId = qiniuRtcManager.renderWithBytesTexture(data, texBuf.getTextureId(), width, height, frontCamera, rotation);
                if (beautyTexId != 0) {
                    texBuf.setTextureId(beautyTexId);
                }
            }

            @Override
            public void onPreviewFrame(byte[] data, int width, int height, int rotation, int fmt, long timestampNs) {
                if (this.data == null || this.data.length != data.length) {
                    this.data = new byte[width * height/2*3];
                }
                System.arraycopy(data,0,this.data,0,this.data.length);
                this.width = width;
                this.height = height;
                this.rotation = rotation;
            }

            @Override
            public void onCaptureStopped() {
                mCaptureStoppedSem.drainPermits();
                mCaptureStoppedSem.release();
                if (qiniuRtcManager != null) {
                    qiniuRtcManager.textureDestoryed();
                }
            }
        });
    }

    /**
     * 初始化本地音视频 track
     * 关于 Track 的概念介绍 https://doc.qnsdk.com/rtn/android/docs/preparation#5
     */
    private void initLocalTrackInfoList() {
        mLocalTrackList = new ArrayList<>();
        mLocalAudioTrack = mEngine.createTrackInfoBuilder()
                .setSourceType(QNSourceType.AUDIO)
                .setMaster(true)
                .create();
        mEngine.setLocalAudioPacketCallback(mLocalAudioTrack, new QNLocalAudioPacketCallback() {
            @Override
            public int onPutExtraData(ByteBuffer extraData, int extraDataMaxSize) {
                // 可以向 extraData 填充自定义数据并在对端通过 QNRemoteAudioPacketCallback 解析
                if (mAddExtraAudioData) {
                    extraData.rewind();
                    extraData.put((byte) 0x11);
                    extraData.flip();
                    return extraData.remaining();
                }
                return 0;
            }

            @Override
            public int onSetMaxEncryptSize(int frameSize) {
                // 当需要根据自己的算法加密音频数据时，需要在该方法告知 SDK 加密后的最大数据大小
                if (mEnableAudioEncrypt) {
                    return frameSize + 10;
                }
                return 0;
            }

            @Override
            public int onEncrypt(ByteBuffer frame, int frameSize, ByteBuffer encryptedFrame) {
                // 自主加密接口，将加密后的数据放置到 encryptedFrame 中，并返回加密后大小
                if (mEnableAudioEncrypt) {
                    encryptedFrame.rewind();
                    frame.rewind();
                    encryptedFrame.put((byte) 0x18);
                    encryptedFrame.put((byte) 0x19);
                    encryptedFrame.put(frame);
                    encryptedFrame.flip();
                    return encryptedFrame.remaining();
                }
                return 0;
            }
        });
        mLocalTrackList.add(mLocalAudioTrack);

        switch (mCaptureMode) {
            case Config.CAMERA_CAPTURE:
                // 创建 Camera 采集的视频 Track
                mLocalVideoTrack = mEngine.createTrackInfoBuilder()
                        .setSourceType(QNSourceType.VIDEO_CAMERA)
                        .setMaster(true)
                        .setTag(UserTrackView.TAG_CAMERA).create();
                mLocalTrackList.add(mLocalVideoTrack);
                break;
            case Config.ONLY_AUDIO_CAPTURE:
                mControlFragment.setAudioOnly(true);
                break;
            case Config.SCREEN_CAPTURE:
                // 创建屏幕录制的视频 Track
                createScreenTrack();
                mLocalTrackList.add(mLocalScreenTrack);
                mControlFragment.setAudioOnly(true);
                break;
            case Config.MUTI_TRACK_CAPTURE:
                // 视频通话 + 屏幕共享两路 track
                createScreenTrack();
                mLocalVideoTrack = mEngine.createTrackInfoBuilder()
                        .setSourceType(QNSourceType.VIDEO_CAMERA)
                        .setTag(UserTrackView.TAG_CAMERA).create();
                mLocalTrackList.add(mLocalScreenTrack);
                mLocalTrackList.add(mLocalVideoTrack);
                break;
        }
    }


    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            QNVideoFormat screenEncodeFormat = new QNVideoFormat(mScreenWidth / 2, mScreenHeight / 2, 15);
            mLocalScreenTrack = mEngine.createTrackInfoBuilder()
                .setSourceType(QNSourceType.VIDEO_SCREEN)
                .setVideoPreviewFormat(screenEncodeFormat)
                .setBitrate(BITRATE_FOR_SCREEN_VIDEO)
                .setMaster(true)
                .setTag(UserTrackView.TAG_SCREEN).create();
            mLocalTrackList.add(mLocalScreenTrack);
            if (mEngine.getRoomState().equals(QNRoomState.CONNECTED) || mEngine.getRoomState().equals(QNRoomState.RECONNECTED)) {
                mEngine.publishTracks(Collections.singletonList(mLocalScreenTrack));
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    // 处理 Build.VERSION_CODES.Q 及以上的兼容问题
    private void createScreenTrack() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent intent = new Intent(this, ForegroundService.class);
            startForegroundService(intent);
            bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
            Log.i(TAG, "start service for Q");
        } else {
            QNVideoFormat screenEncodeFormat = new QNVideoFormat(mScreenWidth / 2, mScreenHeight / 2, 15);
            mLocalScreenTrack = mEngine.createTrackInfoBuilder()
                .setSourceType(QNSourceType.VIDEO_SCREEN)
                .setVideoPreviewFormat(screenEncodeFormat)
                .setBitrate(BITRATE_FOR_SCREEN_VIDEO)
                .setMaster(true)
                .setTag(UserTrackView.TAG_SCREEN).create();
        }
    }

    /**
     * 合流转推、单路转推相关处理
     */
    private void initMergeLayoutConfig() {
        mMergeLayoutConfigView = new MergeLayoutConfigView(this);
        mMergeLayoutConfigView.setRoomId(mRoomId);
        mUserListAdapter = new UserListAdapter();
        mRoomUsersMergeOption = new RTCRoomUsersMergeOption();
        mMergeLayoutConfigView.getUserListView().setAdapter(mUserListAdapter);
        mMergeLayoutConfigView.setOnClickedListener(new MergeLayoutConfigView.OnClickedListener() {
            @Override
            public void onConfirmClicked() {
                // 保存当前用户选择的配置信息
                mMergeLayoutConfigView.updateMergeOptions();

                if (mEngine == null) {
                    return;
                }
                if (!mMergeLayoutConfigView.isStreamingEnabled()) {
                    // 处理停止合流逻辑
                    if (mIsMergeJobStreaming) {
                        // 如果正在推流，则停止之前的合流任务
                        // 传入 null，则处理默认的合流任务
                        mEngine.stopMergeStream(mCurrentMergeJob == null ? null : mCurrentMergeJob.getMergeJobId());
                        mIsMergeJobStreaming = false;
                        ToastUtils.s(RoomActivity.this, "停止合流！！！");
                    } else {
                        ToastUtils.s(RoomActivity.this, "未开启合流，配置未生效！！！");
                    }
                    if (mPopWindow != null) {
                        mPopWindow.dismiss();
                    }
                    return;
                }
                // 如果当前正在进行单路转推任务，那么切换到合流任务的时候，务必使用自定义合流任务，否则可能会出现抢流的现象
                if (mIsForwardJobStreaming && !mMergeLayoutConfigView.isCustomMergeJob()) {
                    Utils.showAlertDialog(RoomActivity.this, getString(R.string.create_merge_job_warning));
                    mMergeLayoutConfigView.updateStreamingStatus(false);
                    if (mPopWindow != null) {
                        mPopWindow.dismiss();
                    }
                    return;
                }
                if (mMergeLayoutConfigView.isCustomMergeJob()) {
                    // 处理自定义合流任务的逻辑
                    QNMergeJob mergeJob = mMergeLayoutConfigView.getCustomMergeJob();
                    if (mergeJob != null) {
                        // 如果正在推流，则停止之前的合流任务
                        if (mIsMergeJobStreaming) {
                            mEngine.stopMergeStream(mCurrentMergeJob == null ? null : mCurrentMergeJob.getMergeJobId());
                        }
                        mCurrentMergeJob = mergeJob;
                        // 创建自定义合流任务
                        mEngine.createMergeJob(mCurrentMergeJob);
                    } else {
                        // 更新合流布局到自定义合流任务
                        setMergeStreamLayouts();
                    }
                } else {
                    // 更新合流布局到默认合流任务
                    setMergeStreamLayouts();
                }
                if (mPopWindow != null) {
                    mPopWindow.dismiss();
                }
            }
        });
    }

    private void logAndToast(final String msg) {
        Log.d(TAG, msg);
        if (mLogToast != null) {
            mLogToast.cancel();
        }
        mLogToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        mLogToast.show();
    }

    private void disconnectWithErrorMessage(final String errorMessage) {
        new AlertDialog.Builder(this)
                .setTitle(getText(R.string.channel_error_title))
                .setMessage(errorMessage)
                .setCancelable(false)
                .setNeutralButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                                finish();
                            }
                        })
                .create()
                .show();
    }

    private void reportError(final String description) {
        // TODO: handle error.
        if (!mIsError) {
            mIsError = true;
            disconnectWithErrorMessage(description);
        }
    }

    private void showKickoutDialog(final String userId) {
        if (mKickOutDialog == null) {
            mKickOutDialog = new AlertDialog.Builder(this)
                    .setNegativeButton(R.string.negative_dialog_tips, null)
                    .create();
        }
        mKickOutDialog.setMessage(getString(R.string.kickout_tips, userId));
        mKickOutDialog.setButton(DialogInterface.BUTTON_POSITIVE, getResources().getString(R.string.positive_dialog_tips),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mEngine.kickOutUser(userId);
                    }
                });
        mKickOutDialog.show();
    }

    private void updateRemoteLogText(final String logText) {
        Log.i(TAG, logText);
        mControlFragment.updateRemoteLogText(logText);
    }

    /**
     * 当新的本地、远端 Track 变化时，重新排列合流画面配置
     */
    private void resetMergeStream() {
        Log.d(TAG, "resetMergeStream()");
        List<QNMergeTrackOption> configuredMergeTracksOptions = new ArrayList<>();

        // video tracks merge layout options.
        List<RTCTrackMergeOption> roomVideoTrackInfoList = mRoomUsersMergeOption.getRTCVideoMergeOptions();
        if (!roomVideoTrackInfoList.isEmpty()) {
            List<QNMergeTrackOption> mergeTrackOptions = SplitUtils.split(roomVideoTrackInfoList.size(),
                    mCurrentMergeJob == null ? QNAppServer.STREAMING_WIDTH : mCurrentMergeJob.getWidth(),
                    mCurrentMergeJob == null ? QNAppServer.STREAMING_HEIGHT : mCurrentMergeJob.getHeight());
            if (mergeTrackOptions.size() != roomVideoTrackInfoList.size()) {
                Log.e(TAG, "split option error.");
                return;
            }

            for (int i = 0; i < mergeTrackOptions.size(); i++) {
                RTCTrackMergeOption trackMergeOption = roomVideoTrackInfoList.get(i);

                if (!trackMergeOption.isTrackInclude()) {
                    continue;
                }
                QNMergeTrackOption item = mergeTrackOptions.get(i);
                trackMergeOption.updateQNMergeTrackOption(item);
                configuredMergeTracksOptions.add(trackMergeOption.getQNMergeTrackOption());
            }
        }

        // audio tracks merge layout options
        List<RTCTrackMergeOption> roomAudioTrackInfoList = mRoomUsersMergeOption.getRTCAudioTracks();
        if (!roomAudioTrackInfoList.isEmpty()) {
            for (RTCTrackMergeOption trackMergeOption : roomAudioTrackInfoList) {
                if (!trackMergeOption.isTrackInclude()) {
                    continue;
                }
                configuredMergeTracksOptions.add(trackMergeOption.getQNMergeTrackOption());
            }
        }

        if (mIsMergeJobStreaming) {
            mEngine.setMergeStreamLayouts(configuredMergeTracksOptions, mCurrentMergeJob == null ? null : mCurrentMergeJob.getMergeJobId());
        }
    }

    private void userJoinedForStreaming(String userId, String userData) {
        mRoomUsersMergeOption.onUserJoined(userId, userData);
        if (mUserListAdapter != null) {
            mUserListAdapter.notifyDataSetChanged();
        }
    }

    private void userLeftForStreaming(String userId, boolean localLeft) {
        if (localLeft) {
            mRoomUsersMergeOption.onUserLeft();
        } else {
            mRoomUsersMergeOption.onUserLeft(userId);
        }
        if (mUserListAdapter != null) {
            mUserListAdapter.notifyDataSetChanged();
        }
    }

    private int updateSerialNum() {
        mMergeLayoutConfigView.updateSerialNum(++mSerialNum);
        return mSerialNum;
    }

    /**
     * 配置各个用户当前选中的 Track 信息到合流布局
     *
     * 如果使用的默认合流任务，则无需手动 createMergeJob，setMergeStreamLayouts 中 jobId 参数传 null 即可
     */
    private void setMergeStreamLayouts() {
        List<RTCTrackMergeOption> userTracks = new ArrayList<>();
        for (int user = 0; user < mRoomUsersMergeOption.size(); user++) {
            RTCUserMergeOptions userMergeOptions = mRoomUsersMergeOption.getRoomUserByPosition(user);
            if (userMergeOptions.getAudioTrack() != null) {
                userTracks.add(userMergeOptions.getAudioTrack());
            }
            if (userMergeOptions.getVideoTracks().size() > 0) {
                userTracks.addAll(userMergeOptions.getVideoTracks());
            }
        }

        List<QNMergeTrackOption> addedTrackOptions = new ArrayList<>();
        List<QNMergeTrackOption> removedTrackOptions = new ArrayList<>();
        for (RTCTrackMergeOption item : userTracks) {
            if (item.isTrackInclude()) {
                addedTrackOptions.add(item.getQNMergeTrackOption());
            } else {
                removedTrackOptions.add(item.getQNMergeTrackOption());
            }
        }
        if (!addedTrackOptions.isEmpty()) {
            // 配置对应 tracks 的合流配置信息
            mEngine.setMergeStreamLayouts(addedTrackOptions, mCurrentMergeJob == null ? null : mCurrentMergeJob.getMergeJobId());
        }
        if (!removedTrackOptions.isEmpty()) {
            // 移除对应 tracks 的合流配置，移除后相应 track 的数据将不会参与合流
            mEngine.removeMergeStreamLayouts(removedTrackOptions, mCurrentMergeJob == null ? null : mCurrentMergeJob.getMergeJobId());
        }
        mIsMergeJobStreaming = true;
        ToastUtils.s(RoomActivity.this, "已发送合流配置，请等待合流画面生效");
    }

    private void showAlertDialog(String text) {

    }

    @TargetApi(19)
    private static int getSystemUiVisibility() {
        int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        return flags;
    }

    /**
     * 房间状态改变时会回调此方法
     * 房间状态回调只需要做提示用户，或者更新相关 UI； 不需要再做加入房间或者重新发布等其他操作！
     * @param state 房间状态，可参考 {@link QNRoomState}
     */
    @Override
    public void onRoomStateChanged(QNRoomState state) {
        Log.i(TAG, "onRoomStateChanged:" + state.name());
        switch (state) {
            case IDLE:
                if (mIsAdmin) {
                    userLeftForStreaming(mUserId, true);
                }
                break;
            case RECONNECTING:
                logAndToast(getString(R.string.reconnecting_to_room));
                mControlFragment.stopTimer();
                break;
            case CONNECTED:
                if (mIsAdmin) {
                    userJoinedForStreaming(mUserId, "");
                }
                // 加入房间后可以进行 tracks 的发布
                mEngine.publishTracks(mLocalTrackList);
                logAndToast(getString(R.string.connected_to_room));
                mIsJoinedRoom = true;
                mControlFragment.startTimer();

                // 重连失败后再次加入房间后，恢复无效的合流任务
                if (mIsMergeJobStreaming && mMergeLayoutConfigView.isCustomMergeJob() && !mMergeLayoutConfigView.isMergeJobValid()) {
                    QNMergeJob mergeJob = mMergeLayoutConfigView.getCustomMergeJob();
                    if (mergeJob != null) {
                        mCurrentMergeJob = mergeJob;
                        // 创建自定义合流任务
                        mEngine.createMergeJob(mCurrentMergeJob);
                   }
                }
                break;
            case RECONNECTED:
                logAndToast(getString(R.string.connected_to_room));
                mControlFragment.startTimer();
                break;
            case CONNECTING:
                logAndToast(getString(R.string.connecting_to, mRoomId));
                break;
        }
    }

    /**
     * 当退出房间执行完毕后触发该回调，可用于切换房间
     */
    @Override
    public void onRoomLeft() {

    }

    /**
     * 远端用户加入房间时会回调此方法
     * @see QNRTCEngine#joinRoom(String, String) 可指定 userData 字段
     *
     * @param remoteUserId 远端用户的 userId
     * @param userData 透传字段，用户自定义内容
     */
    @Override
    public void onRemoteUserJoined(String remoteUserId, String userData) {
        updateRemoteLogText("onRemoteUserJoined:remoteUserId = " + remoteUserId + " ,userData = " + userData);
        if (mIsAdmin) {
            userJoinedForStreaming(remoteUserId, userData);
        }
    }

    @Override
    public void onRemoteUserReconnecting(String remoteUserId) {
        logAndToast("远端用户: " + remoteUserId + " 重连中");
    }

    @Override
    public void onRemoteUserReconnected(String remoteUserId) {
        logAndToast("远端用户: " + remoteUserId + " 重连成功");
    }

    /**
     * 远端用户离开房间时会回调此方法
     *
     * @param remoteUserId 远端离开用户的 userId
     */
    @Override
    public void onRemoteUserLeft(final String remoteUserId) {
        updateRemoteLogText("onRemoteUserLeft:remoteUserId = " + remoteUserId);
        if (mIsAdmin) {
            userLeftForStreaming(remoteUserId, false);
        }
    }

    /**
     * 本地 tracks 成功发布时会回调此方法
     *
     * @param trackInfoList 已发布的 tracks 列表
     */
    @Override
    public void onLocalPublished(List<QNTrackInfo> trackInfoList) {
        updateRemoteLogText("onLocalPublished");
        mEngine.enableStatistics();
        if (mIsAdmin) {
            mRoomUsersMergeOption.onTracksPublished(mUserId, mLocalTrackList);
            resetMergeStream();
        }
    }

    /**
     * 远端用户 tracks 成功发布时会回调此方法
     *
     * @param remoteUserId 远端用户 userId
     * @param trackInfoList 远端用户发布的 tracks 列表
     */
    @Override
    public void onRemotePublished(String remoteUserId, List<QNTrackInfo> trackInfoList) {
        updateRemoteLogText("onRemotePublished:remoteUserId = " + remoteUserId);
        for (QNTrackInfo info : trackInfoList) {
            if (info.isAudio()) {
                mEngine.setRemoteAudioPacketCallback(info, new QNRemoteAudioPacketCallback() {
                    @Override
                    public void onGetExtraData(ByteBuffer extraData, int extraDataSize) {
                        // 如果对端有发送自定义数据，则数据会在 extraData
                        if (extraDataSize > 0) {
                            extraData.rewind();
                            Log.i(TAG, "extra size " + extraDataSize + " data " + extraData.get());
                        }
                    }

                    @Override
                    public int onSetMaxDecryptSize(int encryptedFrameSize) {
                        // 当需要根据自己的算法解密音频数据时，需要在该方法告知 SDK 解密后的最大数据大小
                        if (mEnableAudioEncrypt) {
                            return encryptedFrameSize;
                        } else {
                            return 0;
                        }
                    }

                    @Override
                    public int onDecrypt(ByteBuffer encryptedFrame, int encryptedSize, ByteBuffer frame) {
                        // 自主解密接口，将解密后的数据放置到 frame 中，并返回解密后大小
                        if (mEnableAudioEncrypt) {
                            encryptedFrame.rewind();
                            frame.rewind();
                            if (encryptedFrame.get(0) == 0x18 && encryptedFrame.get(1) == 0x19) {
                                encryptedFrame.position(2);
                                frame.put(encryptedFrame);
                                return encryptedSize - 2;
                            }
                        }
                        return 0;
                    }
                });
            }
        }
        mRoomUsersMergeOption.onTracksPublished(remoteUserId, trackInfoList);
        // 如果希望在远端发布音视频的时候，自动配置合流，则可以在此处重新调用 setMergeStreamLayouts 进行配置
        if (mIsAdmin) {
            resetMergeStream();
        }
    }

    /**
     * 远端用户 tracks 成功取消发布时会回调此方法
     *
     * @param remoteUserId 远端用户 userId
     * @param trackInfoList 远端用户取消发布的 tracks 列表
     */
    @Override
    public void onRemoteUnpublished(final String remoteUserId, List<QNTrackInfo> trackInfoList) {
        updateRemoteLogText("onRemoteUnpublished:remoteUserId = " + remoteUserId);
        if (mTrackWindowMgr != null) {
            mTrackWindowMgr.removeTrackInfo(remoteUserId, trackInfoList);
        }
        mRoomUsersMergeOption.onTracksUnPublished(remoteUserId, trackInfoList);
        if (mIsAdmin) {
            resetMergeStream();
        }
    }

    /**
     * 远端用户成功操作静默 tracks 时会回调此方法
     *
     * @param remoteUserId 远端用户 userId
     * @param trackInfoList 远端用户静默的 tracks 列表，是否静默可以通过读取 {@link QNTrackInfo} 的 isMuted() 方法获取
     */
    @Override
    public void onRemoteUserMuted(String remoteUserId, List<QNTrackInfo> trackInfoList) {
        updateRemoteLogText("onRemoteUserMuted:remoteUserId = " + remoteUserId);
        if (mTrackWindowMgr != null) {
            mTrackWindowMgr.onTrackInfoMuted(remoteUserId);
        }
    }

    /**
     * 成功订阅远端用户的 tracks 时会回调此方法
     *
     * @param remoteUserId 远端用户 userId
     * @param trackInfoList 订阅的远端用户 tracks 列表
     */
    @Override
    public void onSubscribed(String remoteUserId, List<QNTrackInfo> trackInfoList) {
        updateRemoteLogText("onSubscribed:remoteUserId = " + remoteUserId);
        if (mTrackWindowMgr != null) {
            mTrackWindowMgr.addTrackInfo(remoteUserId, trackInfoList);
        }
    }

    /**
     * 订阅远端用户 Track 的 QNTrackSubConfiguration 变化时会回调此方法
     *
     * @param remoteUserId 远端用户 userId
     * @param trackInfoList 订阅的远端用户 tracks 列表
     */
    @Override
    public void onSubscribedProfileChanged(String remoteUserId, List<QNTrackInfo> trackInfoList) {
    }

    /**
     * 当自己被踢出房间时会回调此方法
     *
     * @param userId 踢人方的 userId
     */
    @Override
    public void onKickedOut(String userId) {
        ToastUtils.s(RoomActivity.this, getString(R.string.kicked_by_admin));
        finish();
    }

    /**
     * 当媒体状态更新时会回调此方法
     *
     * QNStatisticsReport#audioPacketLostRate（音频丢包率）和 QNStatisticsReport#videoPacketLostRate (视频丢包率)
     * 可以用来向用户提示自己网络状态不佳（比如，连续一段时间丢包路超过 10%）。
     *
     * @param report 媒体信息，详情请参考 {@link QNStatisticsReport}
     */
    @Override
    public void onStatisticsUpdated(final QNStatisticsReport report) {
        if (report.userId == null || report.userId.equals(mUserId)) {
            if (QNTrackKind.AUDIO.equals(report.trackKind)) {
                final String log = "音频码率:" + report.audioBitrate / 1000 + "kbps \n" +
                        "音频丢包率:" + report.audioPacketLostRate;
                mControlFragment.updateLocalAudioLogText(log);
            } else if (QNTrackKind.VIDEO.equals(report.trackKind)) {
                final String log = "视频码率:" + report.videoBitrate / 1000 + "kbps \n" +
                        "视频丢包率:" + report.videoPacketLostRate + " \n" +
                        "视频的宽:" + report.width + " \n" +
                        "视频的高:" + report.height + " \n" +
                        "视频的帧率:" + report.frameRate;
                mControlFragment.updateLocalVideoLogText(log);
            }
        }
    }

    /**
     * 当收到远端用户的媒体状态时会回调此方法
     *
     * QNStatisticsReport#audioPacketLostRate（音频丢包率）和 QNStatisticsReport#videoPacketLostRate (视频丢包率)
     * 可以用来向用户提示对方用户网络状态不佳（比如，连续一段时间丢包路超过 10%）。
     *
     * @param reports 媒体信息，详情请参考 {@link QNStatisticsReport}
     */
    @Override
    public void onRemoteStatisticsUpdated(List<QNStatisticsReport> reports) {
        for (QNStatisticsReport report : reports) {
            int lost = report.trackKind.equals(QNTrackKind.VIDEO) ? report.videoPacketLostRate : report.audioPacketLostRate;
            Log.i(TAG, "remote user " + report.userId
                + " rtt " + report.rtt
                + " grade " + report.networkGrade
                + " track " + report.trackId
                + " kind " + (report.trackKind.name())
                + " lostRate " + lost);
        }
    }

    /**
     * 当音频路由发生变化时会回调此方法
     *
     * @param routing 音频设备, 详情请参考{@link QNAudioDevice}
     */
    @Override
    public void onAudioRouteChanged(QNAudioDevice routing) {
        updateRemoteLogText("onAudioRouteChanged: " + routing.name());
    }

    /**
     * 当合流任务创建成功的时候会回调此方法
     *
     * @param mergeJobId 合流任务 id
     */
    @Override
    public void onCreateMergeJobSuccess(String mergeJobId) {
        updateSerialNum();
        ToastUtils.s(RoomActivity.this, "合流任务 " + mergeJobId + " 创建成功！");
        setMergeStreamLayouts();

        // 取消单路转推
        if (mIsForwardJobStreaming) {
            // 注意：
            // 1. A 房间中创建的转推任务，只能在 A 房间中进行销毁，无法在其他房间中销毁
            // 2. JOB_STOP_DELAY_TIME 代表转推任务延迟关闭的时间，如果您的场景涉及到房间的切换以及不同转推任务
            // 的切换，为了保证切换场景下播放的连续性，建议您务必添加延迟关闭时间；
            // 3. 如果您的业务场景不涉及到跨房间的转推任务切换，可以不用设置延迟关闭时间，直接调用
            // mEngine.stopForwardJob(mForwardJob.getForwardJobId()) 即可，SDK 默认会立即停止转推任务
            mEngine.stopForwardJob(mForwardJob.getForwardJobId(), JOB_STOP_DELAY_TIME);
            mIsForwardJobStreaming = false;
            mControlFragment.updateForwardJobText(getString(R.string.forward_job_btn_text));
        }
    }

    /**
     * 当单路流转推任务创建成功的时候会回调此方法
     *
     * @param forwardJobId 转推任务 ID
     */
    @Override
    public void onCreateForwardJobSuccess(String forwardJobId) {
        updateSerialNum();
        mControlFragment.updateForwardJobText(getString(R.string.stop_forward_job_text));
        ToastUtils.s(RoomActivity.this, "单路转推任务 " + forwardJobId + " 创建成功！");
        mIsForwardJobStreaming = true;

        // 取消合流转推
        if (mIsMergeJobStreaming && mCurrentMergeJob != null) {
            // 注意：
            // 1. A 房间中创建的转推任务，只能在 A 房间中进行销毁，无法在其他房间中销毁
            // 2. JOB_STOP_DELAY_TIME 代表转推任务延迟关闭的时间，如果您的场景涉及到房间的切换以及不同转推任务
            // 的切换，为了保证切换场景下播放的连续性，建议您务必添加延迟关闭时间；
            // 3. 如果您的业务场景不涉及到跨房间的转推任务切换，可以不用设置延迟关闭时间，直接调用
            // mEngine.stopForwardJob(mForwardJob.getForwardJobId()) 即可，SDK 默认会立即停止转推任务
            mEngine.stopMergeStream(mCurrentMergeJob.getMergeJobId(), JOB_STOP_DELAY_TIME);
            mIsMergeJobStreaming = false;
            mMergeLayoutConfigView.updateStreamingStatus(false);
            mMergeLayoutConfigView.updateMergeJobValid(false);
        }
    }

    /**
     * 当发生错误时会回调此方法
     *
     * @param errorCode 错误码，详情请参考 {@link QNErrorCode}
     * @param description 错误描述
     */
    @Override
    public void onError(int errorCode, String description) {
        /**
         * 关于错误异常的相关处理，都应在该回调中完成; 需要处理的错误码及建议处理逻辑如下:
         *
         *【TOKEN 相关】
         * 1. QNErrorCode.ERROR_TOKEN_INVALID 和 QNErrorCode.ERROR_TOKEN_ERROR 表示您提供的房间 token 不符合七牛 token 签算规则,
         *    详情请参考【服务端开发说明.RoomToken 签发服务】https://doc.qnsdk.com/rtn/docs/server_overview#1
         * 2. QNErrorCode.ERROR_TOKEN_EXPIRED 表示您的房间 token 过期, 需要重新生成 token 再加入；
         *
         *【房间设置相关】以下情况可以与您的业务服务开发确认具体设置
         * 1. QNErrorCode.ERROR_ROOM_FULL 当房间已加入人数超过每个房间的人数限制触发；请确认后台服务的设置；
         * 2. QNErrorCode.ERROR_PLAYER_ALREADY_EXIST 后台如果配置为开启【禁止自动踢人】,则同一用户重复加入/未正常退出再加入会触发此错误，您的业务可根据实际情况选择配置；
         * 3. QNErrorCode.ERROR_NO_PERMISSION 用户对于特定操作，如合流需要配置权限，禁止出现未授权的用户操作；
         * 4. QNErrorCode.ERROR_ROOM_CLOSED 房间已被管理员关闭；
         *
         *【其他错误】
         * 1. QNErrorCode.ERROR_AUTH_FAIL 服务验证时出错，可能为服务网络异常。建议重新尝试加入房间；
         * 2. QNErrorCode.ERROR_PUBLISH_FAIL 发布失败, 会有如下3种情况:
         * 1 ）请确认成功加入房间后，再执行发布操作
         * 2 ）请确定对于音频/视频 Track，分别最多只能有一路为 master
         * 3 ）请确认您的网络状况是否正常

         * 3. QNErrorCode.ERROR_RECONNECT_TOKEN_ERROR 内部重连后出错，一般出现在网络非常不稳定时出现，建议提示用户并尝试重新加入房间；
         *    另外，当前用户之前创建的合流任务、单路转推任务将会被服务销毁，重新加入房间后应该重新创建合流，单路转推任务 ！！！
         * 4. QNErrorCode.ERROR_INVALID_PARAMETER 服务交互参数错误，请在开发时注意合流、踢人动作等参数的设置。
         * 5. QNErrorCode.ERROR_DEVICE_CAMERA 系统摄像头错误, 建议提醒用户检查
         */
        switch (errorCode) {
            case QNErrorCode.ERROR_TOKEN_INVALID:
            case QNErrorCode.ERROR_TOKEN_ERROR:
                logAndToast("roomToken 错误，请检查后重新生成，再加入房间");
                break;
            case QNErrorCode.ERROR_TOKEN_EXPIRED:
                logAndToast("roomToken过期");
                mRoomToken = QNAppServer.getInstance().requestRoomToken(RoomActivity.this, mUserId, mRoomId);
                mEngine.joinRoom(mRoomToken);
                break;
            case QNErrorCode.ERROR_ROOM_FULL:
                logAndToast("房间人数已满!");
                break;
            case QNErrorCode.ERROR_PLAYER_ALREADY_EXIST:
                logAndToast("不允许同一用户重复加入");
                break;
            case QNErrorCode.ERROR_NO_PERMISSION:
                logAndToast("请检查用户权限:" + description);
                break;
            case QNErrorCode.ERROR_INVALID_PARAMETER:
                logAndToast("请检查参数设置:" + description);
                break;
            case QNErrorCode.ERROR_PUBLISH_FAIL: {
                if (mEngine.getRoomState() != QNRoomState.CONNECTED
                    && mEngine.getRoomState() != QNRoomState.RECONNECTED) {
                    logAndToast("发布失败，请加入房间发布: " + description);
                    mEngine.joinRoom(mRoomToken);
                } else {
                    logAndToast("发布失败: " + description);
                    mEngine.publishTracks(mLocalTrackList);
                }
            }
            break;
            case QNErrorCode.ERROR_AUTH_FAIL:
            case QNErrorCode.ERROR_RECONNECT_TOKEN_ERROR: {
                // reset TrackWindowMgr
                mTrackWindowMgr.reset();
                // display local videoTrack
                List<QNTrackInfo> localTrackListExcludeScreenTrack = new ArrayList<>(mLocalTrackList);
                localTrackListExcludeScreenTrack.remove(mLocalScreenTrack);
                mTrackWindowMgr.addTrackInfo(mUserId, localTrackListExcludeScreenTrack);
                if (errorCode == QNErrorCode.ERROR_RECONNECT_TOKEN_ERROR) {
                    logAndToast("ERROR_RECONNECT_TOKEN_ERROR 即将重连，请注意网络质量！");
                    // 当重连超时后，用户创建的合流任务默认被销毁；需要重新创建合流任务
                    if (mIsMergeJobStreaming && mMergeLayoutConfigView.isCustomMergeJob()) {
                        mMergeLayoutConfigView.updateMergeJobValid(false);
                    }
                }
                if (errorCode == QNErrorCode.ERROR_AUTH_FAIL) {
                    logAndToast("ERROR_AUTH_FAIL 即将重连");
                }
                // rejoin Room
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mEngine.joinRoom(mRoomToken);
                    }
                }, 1000);
            }
            break;
            case QNErrorCode.ERROR_ROOM_CLOSED:
            reportError("房间被关闭");
            break;
            case QNErrorCode.ERROR_DEVICE_CAMERA:
            logAndToast("请检查摄像头权限，或者被占用");
            break;
            default:
            logAndToast("errorCode:" + errorCode + " description:" + description);
            break;
        }
    }

    /**
     * 当收到自定义消息时回调此方法
     *
     * @param message 自定义信息，详情请参考 {@link QNCustomMessage}
     */
    @Override
    public void onMessageReceived(QNCustomMessage message) {

    }

    // Demo control
    @Override
    public void onCallHangUp() {
        if (mEngine != null) {
            mEngine.leaveRoom();
        }
        finish();
    }

    @Override
    public void onCameraSwitch() {
        if (mEngine != null) {
            mEngine.switchCamera(new QNCameraSwitchResultCallback() {
                @Override
                public void onCameraSwitchDone(boolean isFrontCamera) {
                    frontCamera = isFrontCamera;
                }

                @Override
                public void onCameraSwitchError(String errorMessage) {
                }
            });
        }
    }

    @Override
    public boolean onToggleMic() {
        if (mEngine != null && mLocalAudioTrack != null) {
            mMicEnabled = !mMicEnabled;
            mLocalAudioTrack.setMuted(!mMicEnabled);
            mEngine.muteTracks(Collections.singletonList(mLocalAudioTrack));
            if (mTrackWindowMgr != null) {
                mTrackWindowMgr.onTrackInfoMuted(mUserId);
            }
        }
        return mMicEnabled;
    }

    @Override
    public boolean onToggleVideo() {
        if (mEngine != null && mLocalVideoTrack != null) {
            mVideoEnabled = !mVideoEnabled;
            mLocalVideoTrack.setMuted(!mVideoEnabled);
            if (mLocalScreenTrack != null) {
                mLocalScreenTrack.setMuted(!mVideoEnabled);
                mEngine.muteTracks(Arrays.asList(mLocalScreenTrack, mLocalVideoTrack));
            } else {
                mEngine.muteTracks(Collections.singletonList(mLocalVideoTrack));
            }
            if (mTrackWindowMgr != null) {
                mTrackWindowMgr.onTrackInfoMuted(mUserId);
            }
        }
        return mVideoEnabled;
    }

    @Override
    public boolean onToggleSpeaker() {
        if (mEngine != null) {
            mSpeakerEnabled = !mSpeakerEnabled;
            mEngine.muteRemoteAudio(!mSpeakerEnabled);
        }
        return mSpeakerEnabled;
    }

    @Override
    public boolean onToggleBeauty() {
        if (mEngine != null) {
            mBeautyEnabled = !mBeautyEnabled;
            QNBeautySetting beautySetting = new QNBeautySetting(0.5f, 0.5f, 0.5f);
            beautySetting.setEnable(mBeautyEnabled);
            mEngine.setBeauty(beautySetting);
        }
        return mBeautyEnabled;
    }

    @Override
    public void onCallStreamingConfig() {
        if (!mIsAdmin) {
            ToastUtils.s(RoomActivity.this, "只有 \"admin\" 用户可以开启合流转推！！！");
            return;
        }
        //配置页
        if (mRoomUsersMergeOption.size() == 0) {
            return;
        }
        mChooseUser = mRoomUsersMergeOption.getRoomUserByPosition(0);
        mMergeLayoutConfigView.updateConfigInfo(mChooseUser);
        mMergeLayoutConfigView.updateMergeJobConfigInfo();
        mUserListAdapter.notifyDataSetChanged();

        if (mPopWindow == null) {
            mPopWindow = new PopupWindow(mMergeLayoutConfigView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
            mPopWindow.setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(this, R.color.popupWindowBackground)));
        }
        mPopWindow.showAtLocation(getWindow().getDecorView().getRootView(), Gravity.BOTTOM, 0, 0);
    }

    @Override
    public void onToggleForwardJob() {
        if (!mIsAdmin) {
            ToastUtils.s(RoomActivity.this, "只有 \"admin\" 用户可以开启单流转推！！！");
            return;
        }
        if (!mIsForwardJobStreaming) {
            // 如果当前正在进行默认合流任务的转推，则不允许切换成单路转推，需要停止默认合流任务或者使用自定义合流任务
            if (mIsMergeJobStreaming && mCurrentMergeJob == null) {
                Utils.showAlertDialog(this, getString(R.string.create_forward_job_warning));
                return;
            }
            if (mForwardJob == null) {
                mForwardJob = new QNForwardJob();
                mForwardJob.setForwardJobId(mRoomId);
                mForwardJob.setAudioTrack(mLocalAudioTrack);
                switch (mCaptureMode) {
                    case Config.CAMERA_CAPTURE:
                    case Config.MUTI_TRACK_CAPTURE:
                        mForwardJob.setVideoTrack(mLocalVideoTrack);
                        break;
                    case Config.SCREEN_CAPTURE:
                        mForwardJob.setVideoTrack(mLocalScreenTrack);
                        break;
                }
            }
            mForwardJob.setPublishUrl(String.format(getResources().getString(R.string.publish_url), mRoomId, mSerialNum));
            mEngine.createForwardJob(mForwardJob);
        } else {
            mEngine.stopForwardJob(mForwardJob.getForwardJobId());
            mIsForwardJobStreaming = false;
            mControlFragment.updateForwardJobText(getString(R.string.forward_job_btn_text));
            ToastUtils.s(RoomActivity.this, "已停止 id=" + mForwardJob.getForwardJobId() + " 的单流转推！！！");
        }
    }

    /**
     * 用户合流配置相关
     */
    private class UserListAdapter extends RecyclerView.Adapter<ViewHolder> {
        int[] mColor = {
                Color.parseColor("#588CEE"),
                Color.parseColor("#F8CF5F"),
                Color.parseColor("#4D9F67"),
                Color.parseColor("#F23A48")
        };

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(getApplicationContext()).inflate(R.layout.item_user, parent, false));
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            RTCUserMergeOptions RTCUserMergeOptions = mRoomUsersMergeOption.getRoomUserByPosition(position);
            String userId = RTCUserMergeOptions.getUserId();
            holder.username.setText(userId);
            holder.username.setCircleColor(mColor[position % 4]);
            if (mChooseUser != null && mChooseUser.getUserId().equals(userId)) {
                holder.itemView.setBackground(getResources().getDrawable(R.drawable.white_background));
            } else {
                holder.itemView.setBackgroundResource(0);
            }
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mChooseUser = mRoomUsersMergeOption.getRoomUserByPosition(holder.getAdapterPosition());
                    mMergeLayoutConfigView.updateConfigInfo(mChooseUser);
                    notifyDataSetChanged();
                }
            });
        }

        @Override
        public int getItemCount() {
            return mRoomUsersMergeOption.size();
        }
    }

    private class ViewHolder extends RecyclerView.ViewHolder {
        CircleTextView username;

        private ViewHolder(View itemView) {
            super(itemView);
            username = (CircleTextView) itemView.findViewById(R.id.user_name_text);
        }
    }
}
