package com.alorma.github.ui.activity;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.alorma.github.BuildConfig;
import com.alorma.github.R;
import com.alorma.github.sdk.bean.dto.response.Token;
import com.alorma.github.sdk.bean.dto.response.User;
import com.alorma.github.sdk.security.ApiConstants;
import com.alorma.github.sdk.security.StoreCredentials;
import com.alorma.github.sdk.services.client.BaseClient;
import com.alorma.github.sdk.services.login.RequestTokenClient;
import com.alorma.github.sdk.services.user.GetAuthUserClient;
import com.alorma.github.ui.ErrorHandler;
import com.crashlytics.android.Crashlytics;

import dmax.dialog.SpotsDialog;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class LoginActivity extends AccountAuthenticatorActivity implements BaseClient.OnResultCallback<User> {

    public static final String EXTRA_CLEAR = "EXTRA_CLEAR";
    public static final String ARG_ACCOUNT_TYPE = "ARG_ACCOUNT_TYPE";
    public static final String ARG_AUTH_TYPE = "ARG_AUTH_TYPE";
    public static final String ADDING_FROM_ACCOUNTS = "ADDING_FROM_ACCOUNTS";
    public static final String ACCOUNT_SCOPES = "ACCOUNT_SCOPES";
    public static final String USER_PIC = "USER_PIC";
    public static final String USER_MAIL = "USER_MAIL";

    public static String OAUTH_URL = "https://github.com/login/oauth/authorize";

    private SpotsDialog progressDialog;
    private WebView webview;
    private String accessToken;
    private String scope;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!BuildConfig.DEBUG) {
            Crashlytics.start(this);
        }


        AccountManager accountManager = AccountManager.get(this);
        Account[] accounts = accountManager.getAccountsByType(getString(R.string.account_type));

        boolean fromCreate = getIntent().getBooleanExtra(ADDING_FROM_ACCOUNTS, false);

        if (!fromCreate && accounts != null && accounts.length > 0) {
            openMain();
        } else {
            CookieSyncManager.createInstance(this);
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.removeAllCookie();

            setContentView(R.layout.activity_login);

            webview = (WebView) findViewById(R.id.webview);
            webview.getSettings().setJavaScriptEnabled(true);
            webview.setWebViewClient(new WebViewCustomClient());

            webview.clearCache(true);
            webview.clearFormData();
            webview.clearHistory();
            webview.clearMatches();
            webview.clearSslPreferences();

            webview.getSettings().setUseWideViewPort(true);

            login();
        }
    }

    private void login() {
        String url = OAUTH_URL + "?client_id=" + ApiConstants.CLIENT_ID;

        url = url + "&scope=gist,user,name,notifications,repo";
        webview.loadUrl(url);
    }

/*    private void updatingTokens() {
        if (credentials.scopes() == null || (!credentials.scopes().contains("repo"))) {
            MaterialDialog.Builder builder = new MaterialDialog.Builder(this);
            builder.title(R.string.repo_scope_title);
            builder.content(R.string.repo_scope_message);
            builder.positiveText(R.string.repo_scope_positive);
            builder.cancelable(false);
            builder.callback(new MaterialDialog.ButtonCallback() {
                @Override
                public void onPositive(MaterialDialog dialog) {
                    super.onPositive(dialog);
                    credentials.clear();
                    login();
                }
            });
            builder.show();
        } else {
            MainActivity.startActivity(this);
            finish();
        }
    }*/

    private void openMain() {
        MainActivity.startActivity(LoginActivity.this);
        finish();
    }

    private void endAccess(String accessToken, String scope) {
        this.accessToken = accessToken;
        this.scope = scope;

        GetAuthUserClient userClient = new GetAuthUserClient(this, accessToken);
        userClient.setOnResultCallback(this);
        userClient.execute();
    }

    @Override
    public void onResponseOk(User user, Response r) {
        Account account = new Account(user.login, getString(R.string.account_type));
        Bundle userData = new Bundle();
        userData.putString(LoginActivity.ACCOUNT_SCOPES, scope);
        userData.putString(LoginActivity.USER_PIC, user.avatar_url);
        userData.putString(LoginActivity.USER_MAIL, user.email);
        userData.putString(AccountManager.KEY_AUTHTOKEN, accessToken);

        AccountManager accountManager = AccountManager.get(this);

        accountManager.addAccountExplicitly(account, null, userData);
        accountManager.setAuthToken(account, getString(R.string.account_type), accessToken);

        Bundle result = new Bundle();
        result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
        result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
        result.putString(AccountManager.KEY_AUTHTOKEN, accessToken);

        setAccountAuthenticatorResult(result);

        if (getIntent().getBooleanExtra(ADDING_FROM_ACCOUNTS, false)) {
            Intent intent = new Intent();
            intent.putExtras(result);
            setResult(RESULT_OK, intent);
            finish();
        } else {
            openMain();
        }
    }

    @Override
    public void onFail(RetrofitError error) {

    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELED);
        finish();
    }

    private void showDialog() {
        try {
            progressDialog = new SpotsDialog(this, R.style.SpotDialog_Login);
            progressDialog.setCancelable(false);
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.show();
        } catch (Exception e) {
            e.printStackTrace();
            Crashlytics.logException(e);
        }
    }

    private class WebViewCustomClient extends WebViewClient implements BaseClient.OnResultCallback<Token> {
        private RequestTokenClient requestTokenClient;

        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            String accessTokenFragment = "access_token=";
            String accessCodeFragment = "code";

            // We hijack the GET request to extract the OAuth parameters

            if (url != null) {
                if (url.contains(accessCodeFragment)) {
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
                if (progressDialog != null) {
                    progressDialog.dismiss();
                }
                endAccess(token.access_token, token.scope);
            } else if (token.error != null) {
                Toast.makeText(LoginActivity.this, token.error, Toast.LENGTH_LONG).show();
            }
        }

        @Override
        public void onFail(RetrofitError error) {
            ErrorHandler.onRetrofitError(LoginActivity.this, "WebViewCustomClient", error);
        }
    }
}
