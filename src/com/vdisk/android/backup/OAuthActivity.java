package com.vdisk.android.backup;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import com.idwtwt.extrabackup.R;
import com.sina.weibo.sdk.auth.Oauth2AccessToken;
import com.sina.weibo.sdk.auth.WeiboAuth;
import com.sina.weibo.sdk.auth.WeiboAuthListener;
import com.sina.weibo.sdk.auth.sso.SsoHandler;
import com.sina.weibo.sdk.exception.WeiboException;
import com.sina.weibo.sdk.login.AccessTokenKeeper;
import com.sina.weibo.sdk.login.Constants;
import com.vdisk.android.VDiskAuthSession;
import com.vdisk.android.VDiskDialogListener;
import com.vdisk.net.exception.VDiskDialogError;
import com.vdisk.net.exception.VDiskException;
import com.vdisk.net.exception.VDiskServerException;
import com.vdisk.net.session.AccessToken;
import com.vdisk.net.session.AppKeyPair;
import com.vdisk.net.session.Session.AccessType;
import com.vdisk.net.session.WeiboAccessToken;

public class OAuthActivity extends Activity implements VDiskDialogListener {

	/**
	 * 替换为开发者应用的appkey，例如"16*****960";
	 * 
	 * Replace it to the appkey of developer's application, such as
	 * "16*****960";
	 */
	public static final String CONSUMER_KEY = "1788711150";

	/**
	 * 替换为开发者应用的app secret，例如"94098*****************861f9";
	 * 
	 * Replace it to the app secret of developer's application, such as
	 * "94098*****************861f9";
	 */
	public static final String CONSUMER_SECRET = "983ca0f83764a34a3a6eb99cf37f598c";

	/**
	 * 替换为微博的access_token. 如果你想使用微博token直接访问微盘的API，这个字段不能为空。
	 * 
	 * Replace it to the access_token of WEIBO. If you use weibo token to access
	 * VDisk API, this field should not be null.
	 */
	public static String WEIBO_ACCESS_TOKEN = "WEIBO_ACCESS_TOKEN";

	/**
	 * 
	 * 此处应该替换为与appkey对应的应用回调地址，对应的应用回调地址可在开发者登陆新浪微盘开发平台之后，进入"我的应用--编辑应用信息--回调地址"
	 * 进行设置和查看，如果使用微盘token登陆的话， 应用回调页不可为空。
	 * 
	 * The content of this field should replace with the application's redirect
	 * url of corresponding appkey. Developers can login in Sina VDisk
	 * Development Platform and enter "我的应用--编辑应用信息--回调地址" to set and view the
	 * corresponding application's redirect url. If you use VDisk token, the
	 * redirect url should not be empty. should not be empty.
	 */
	private static final String REDIRECT_URL = "http://vdisk.weibo.com/";

	private Button btn_oauth;
	private CheckBox cbUseWeiboToken;
	private VDiskAuthSession session;
	private AccessToken mVDiskAccessToken;
	private ProgressDialog dialog;

	// 微博授权认证相关
	// Weibo authorization-related
	private WeiboAuth mWeiboAuth;
	private Oauth2AccessToken mWeiboAccessToken;
	private SsoHandler mSsoHandler;

	private static final int SUCCESS = 0;
	private static final int FAILED = -1;
	private Handler handler = new Handler() {

		public void handleMessage(android.os.Message msg) {

			Bundle values = msg.getData();
			switch (msg.what) {
			case SUCCESS: {
				String error = values.getString("error");
				String error_code = values.getString("error_code");

				if (error == null && error_code == null) {
					OAuthActivity.this.onComplete(values);
					session.updateOAuth2Preference(OAuthActivity.this,
							mVDiskAccessToken);
				} else if (error.equals("access_denied")) {
					// 用户或授权服务器拒绝授予数据访问权限
					// User or authorization server refuses to grant data access
					// permission
					OAuthActivity.this.onCancel();
				} else {
					OAuthActivity.this
							.onVDiskException(new VDiskServerException(error,
									Integer.parseInt(error_code)));
				}
				dialog.dismiss();
			}
				break;
			case FAILED: {
				VDiskException e = (VDiskException) values
						.getSerializable("error");
				OAuthActivity.this.onVDiskException(e);
				dialog.dismiss();
			}
				break;
			default:
				break;
			}

		};
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		/**
		 * 初始化 Init
		 */
		AppKeyPair appKeyPair = new AppKeyPair(CONSUMER_KEY, CONSUMER_SECRET);
		/**
		 * @AccessType.APP_FOLDER - sandbox 模式
		 * @AccessType.VDISK - basic 模式
		 */
		session = VDiskAuthSession.getInstance(this, appKeyPair,
				AccessType.APP_FOLDER);

		setContentView(R.layout.auth_main);
		initViews();

		// 创建微博认证实例，读取微博Token
		// Create a instace of Weibo authorization, and read Weibo Token.
		mWeiboAuth = new WeiboAuth(this, Constants.APP_KEY,
				Constants.REDIRECT_URL, Constants.SCOPE);
		mWeiboAccessToken = AccessTokenKeeper.readAccessToken(this);
		if (mWeiboAccessToken.isSessionValid()) {
			WeiboAccessToken weiboToken = new WeiboAccessToken();
			weiboToken.mAccessToken = mWeiboAccessToken.getToken();
			session.enabledAndSetWeiboAccessToken(weiboToken);
		}

		mVDiskAccessToken = VDiskAuthSession.getOAuth2Preference(this,
				appKeyPair);
		// 如果已经登录，直接跳转到测试页面
		// If you are already logged in, jump to the test page directly.
		if (session.isLinked()) {
			startActivity(new Intent(this, VDiskTestActivity.class));
			finish();
		} else if (!TextUtils.isEmpty(mVDiskAccessToken.mRefreshToken)
				&& !VDiskAuthSession.isSessionValid(mVDiskAccessToken)) {
			// 如果微盘AccessToken过期，使用RefreshToken刷新AccessToken并登录
			// If VDisk AccessToken has expired ,use RefreshToken to refresh the
			// VDisk AccessToken then login.
			dialog = new ProgressDialog(this);
			dialog.setMessage(getString(R.string.wait_for_load));

			refreshLogin();
		}
	}

	private void initViews() {

		cbUseWeiboToken = (CheckBox) this.findViewById(R.id.cb_use_weibo_token);

		btn_oauth = (Button) this.findViewById(R.id.btnOAuth);
		btn_oauth.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

				if (cbUseWeiboToken.isChecked()) {

					if (mWeiboAccessToken.isSessionValid()) {
						weiboFastLogin();
					} else {
						ssoAuthorize();
					}

				} else {
					// 使用微盘Token认证，需设置重定向网址
					// Need to set REDIRECT_URL if you want to use VDisk token.
					session.setRedirectUrl(REDIRECT_URL);
					session.authorize(OAuthActivity.this, OAuthActivity.this);
				}

			}
		});
	}

	/**
	 * 
	 * 使用RefreshToken刷新AccessToken并登录
	 * 
	 * Use RefreshToken to refresh the VDisk AccessToken then login.
	 * 
	 */
	private void refreshLogin() {
		dialog.show();
		new Thread() {

			public void run() {
				Message msg = new Message();
				try {
					mVDiskAccessToken = session
							.refreshOAuth2AccessToken(
									mVDiskAccessToken.mRefreshToken,
									OAuthActivity.this);
				} catch (VDiskException e) {
					msg.what = FAILED;
					Bundle error = new Bundle();
					error.putSerializable("error", e);
					msg.setData(error);
					handler.sendMessage(msg);
					return;
				}
				Bundle values = new Bundle();
				values.putSerializable(VDiskAuthSession.OAUTH2_TOKEN,
						mVDiskAccessToken);
				msg.setData(values);
				msg.what = SUCCESS;
				handler.sendMessage(msg);
			};
		}.start();
	}

	/**
	 * 认证结束后的回调方法
	 * 
	 * Callback method after authentication.
	 */
	@Override
	public void onComplete(Bundle values) {

		if (values != null) {
			AccessToken mToken = (AccessToken) values
					.getSerializable(VDiskAuthSession.OAUTH2_TOKEN);
			session.finishAuthorize(mToken);
		}

		startActivity(new Intent(this, VDiskTestActivity.class));
		finish();
	}

	/**
	 * 认证出错的回调方法
	 * 
	 * Callback method for authentication errors.
	 */
	@Override
	public void onError(VDiskDialogError error) {
		Toast.makeText(getApplicationContext(),
				"Auth error : " + error.getMessage(), Toast.LENGTH_LONG).show();
	}

	/**
	 * 认证异常的回调方法
	 * 
	 * Callback method for authentication exceptions.
	 */
	@Override
	public void onVDiskException(VDiskException exception) {
		Toast.makeText(getApplicationContext(),
				"Auth exception : " + exception.getMessage(), Toast.LENGTH_LONG)
				.show();
	}

	/**
	 * 认证被取消的回调方法
	 * 
	 * Callback method as authentication is canceled.
	 */
	@Override
	public void onCancel() {
		Toast.makeText(getApplicationContext(), "Auth cancel",
				Toast.LENGTH_LONG).show();
	}

	/**
	 * 使用微博 Token 登录微盘
	 * 
	 * Use Weibo token for authentication
	 */
	private void weiboFastLogin() {

		WeiboAccessToken weiboToken = new WeiboAccessToken();

		OAuthActivity.WEIBO_ACCESS_TOKEN = mWeiboAccessToken.getToken();
		weiboToken.mAccessToken = OAuthActivity.WEIBO_ACCESS_TOKEN;

		// 开启使用微博Token的开关,如果要使用微博Token的话，必须执行此方法
		// Run this method if you want to use Weibo token.
		session.enabledAndSetWeiboAccessToken(weiboToken);

		session.authorize(OAuthActivity.this, OAuthActivity.this);
	}

	/**
	 * 进行微博 SSO 认证
	 * 
	 * Doing Weibo SSO authentication
	 */
	private void ssoAuthorize() {
		mSsoHandler = new SsoHandler(OAuthActivity.this, mWeiboAuth);
		mSsoHandler.authorize(new AuthListener());
	}

	/**
	 * 当 SSO 授权 Activity 退出时，该函数被调用。
	 * 
	 * When the SSO authorized Activity exits, this function will be invoked.
	 * 
	 * @see {@link Activity#onActivityResult}
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		// SSO 授权回调 Authorization callback
		// 重要：发起 SSO 登陆的 Activity 必须重写 onActivityResult
		// Important: onActivityResult() must be override in Activity if you
		// want to start Weibo SSO authorization.
		//
		if (mSsoHandler != null) {
			mSsoHandler.authorizeCallBack(requestCode, resultCode, data);
		}
	}

	/**
	 * 微博认证授权回调类。 1. SSO 授权时，需要在 {@link #onActivityResult} 中调用
	 * {@link SsoHandler#authorizeCallBack} 后， 该回调才会被执行。 2. 非 SSO
	 * 授权时，当授权结束后，该回调就会被执行。 当授权成功后，请保存该 access_token、expires_in、uid 等信息到
	 * SharedPreferences 中。
	 * 
	 * After Weibo SSO authorization, this callback will be executed.
	 * 
	 */
	class AuthListener implements WeiboAuthListener {

		@Override
		public void onComplete(Bundle values) {
			// 从 Bundle 中解析 Token
			mWeiboAccessToken = Oauth2AccessToken.parseAccessToken(values);
			if (mWeiboAccessToken.isSessionValid()) {

				// 保存 Token 到 SharedPreferences
				// Save the Token to the SharedPreferences
				AccessTokenKeeper.writeAccessToken(OAuthActivity.this,
						mWeiboAccessToken);
				Toast.makeText(OAuthActivity.this,
						R.string.weibosdk_demo_toast_auth_success,
						Toast.LENGTH_SHORT).show();

				weiboFastLogin();
			} else {
				// 当您注册的应用程序签名不正确时，就会收到 Code，请确保签名正确
				// When your register application signature is not correct, will
				// receive the Code, please make sure the signature is correct.
				String code = values.getString("code");
				String message = getString(R.string.weibosdk_demo_toast_auth_failed);
				if (!TextUtils.isEmpty(code)) {
					message = message + "\nObtained the code: " + code;
				}
				Toast.makeText(OAuthActivity.this, message, Toast.LENGTH_LONG)
						.show();
			}
		}

		@Override
		public void onCancel() {
			Toast.makeText(OAuthActivity.this,
					R.string.weibosdk_demo_toast_auth_canceled,
					Toast.LENGTH_LONG).show();
		}

		@Override
		public void onWeiboException(WeiboException e) {
			Toast.makeText(OAuthActivity.this,
					"Auth exception : " + e.getMessage(), Toast.LENGTH_LONG)
					.show();
		}
	}

}
