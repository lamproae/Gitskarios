package com.alorma.github.ui.activity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.util.Log;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.alorma.github.BuildConfig;
import com.alorma.github.R;
import com.alorma.github.sdk.bean.dto.response.Token;
import com.alorma.github.sdk.security.ApiConstants;
import com.alorma.github.sdk.security.StoreCredentials;
import com.alorma.github.sdk.services.client.BaseClient;
import com.alorma.github.sdk.services.login.RequestTokenClient;
import com.bugsense.trace.BugSenseHandler;

import retrofit.RetrofitError;
import retrofit.client.Response;

public class LoginActivity extends Activity {

	public static String OAUTH_URL = "https://github.com/login/oauth/authorize";

	private StoreCredentials credentials;
	private WebView webview;
	private ProgressDialog progressDialog;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (!BuildConfig.DEBUG) {
			BugSenseHandler.initAndStartSession(LoginActivity.this, "77b1f1f6");
		}

		setContentView(R.layout.activity_login);

		credentials = new StoreCredentials(this);
		if (credentials.token() != null) {
			MainActivity.startActivity(this);
			finish();
		} else {
			String url = OAUTH_URL + "?client_id=" + ApiConstants.CLIENT_ID;

			url = url + "&scope=gist,user,repo,notifications";

			webview = (WebView) findViewById(R.id.webview);
			webview.getSettings().setJavaScriptEnabled(true);
			webview.setWebViewClient(new WebViewCustomClient());

			webview.clearCache(true);
			webview.clearFormData();
			webview.clearHistory();
			webview.clearMatches();
			webview.clearSslPreferences();

			webview.getSettings().setUseWideViewPort(true);

			webview.loadUrl(url);
		}
	}

	private void endAcces(String accessToken) {
		credentials.storeToken(accessToken);
		MainActivity.startActivity(this);
		finish();
	}

	@Override
	public void onBackPressed() {
		setResult(RESULT_CANCELED);
		finish();
	}

	private void showDialog() {
		progressDialog = new ProgressDialog(this);
		progressDialog.setIndeterminate(true);
		progressDialog.setMessage(getString(R.string.acces_token_request));
		progressDialog.setCancelable(false);
		progressDialog.setCanceledOnTouchOutside(false);
		progressDialog.show();
	}

	private class WebViewCustomClient extends WebViewClient implements BaseClient.OnResultCallback<Token> {
		private RequestTokenClient requestTokenClient;

		public void onPageStarted(WebView view, String url, Bitmap favicon) {
			// TODO START LOADING
			String accessTokenFragment = "access_token=";
			String accessCodeFragment = "code";

			// We hijack the GET request to extract the OAuth parameters

			if (url.contains(accessTokenFragment)) {
				// the GET request contains directly the token
				String accessToken = url.substring(url.indexOf(accessTokenFragment));

				endAcces(accessToken);
			} else if (url.contains(accessCodeFragment)) {
				// the GET request contains an authorization code

				Uri uri = Uri.parse(url);

				showDialog();

				if (requestTokenClient == null) {
					requestTokenClient = new RequestTokenClient(LoginActivity.this, uri.getQueryParameter(accessCodeFragment));
					requestTokenClient.setOnResultCallback(this);
					requestTokenClient.execute();
				}
			}
		}

		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			Uri uri = Uri.parse(url);
			Uri callback = Uri.parse(ApiConstants.CLIENT_CALLBACK);
			return (uri.getAuthority().equals(callback.getAuthority()));
		}

		@Override
		public void onPageFinished(WebView view, String url) {
			super.onPageFinished(view, url);
			// TODO STOP LOADING
		}

		@Override
		public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
			super.onReceivedSslError(view, handler, error);
			handler.proceed();
		}

		@Override
		public void onResponseOk(Token token, Response r) {
			if (token.access_token != null) {
				progressDialog.hide();
				endAcces(token.access_token);
			} else if (token.error != null) {
				Toast.makeText(LoginActivity.this, token.error, Toast.LENGTH_LONG).show();
			}
		}

		@Override
		public void onFail(RetrofitError error) {
			if (error.getResponse() != null) {
				Log.e("RETROFIT", "Response error body: " + error.getResponse().getBody());
			}
		}
	}
}
