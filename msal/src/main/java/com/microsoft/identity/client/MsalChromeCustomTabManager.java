//  Copyright (c) Microsoft Corporation.
//  All rights reserved.
//
//  This code is licensed under the MIT License.
//
//  Permission is hereby granted, free of charge, to any person obtaining a copy
//  of this software and associated documentation files(the "Software"), to deal
//  in the Software without restriction, including without limitation the rights
//  to use, copy, modify, merge, publish, distribute, sublicense, and / or sell
//  copies of the Software, and to permit persons to whom the Software is
//  furnished to do so, subject to the following conditions :
//
//  The above copyright notice and this permission notice shall be included in
//  all copies or substantial portions of the Software.
//
//  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
//  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
//  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
//  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
//  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
//  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
//  THE SOFTWARE.

package com.microsoft.identity.client;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.support.customtabs.CustomTabsClient;
import android.support.customtabs.CustomTabsIntent;
import android.support.customtabs.CustomTabsServiceConnection;
import android.support.customtabs.CustomTabsSession;

import com.microsoft.identity.common.exception.ErrorStrings;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MsalChromeCustomTabManager {

    private static final String TAG = MsalChromeCustomTabManager.class.getSimpleName();
    private MsalCustomTabsServiceConnection mCustomTabsServiceConnection;
    private CustomTabsIntent mCustomTabsIntent;
    private String mChromePackageWithCustomTabSupport;
    private Activity mParentActivity;
    private static final long CUSTOM_TABS_MAX_CONNECTION_TIMEOUT = 1L;

    private static final String SCHEME_HTTP = "http";
    private static final String SCHEME_HTTPS = "https";

    /**
     * Constructor of MsalChromeCustomTabManager.
     *
     * @param activity Instance of calling activity.
     */
    public MsalChromeCustomTabManager(final Activity activity) {
        if (activity == null) {
            throw new IllegalArgumentException("Activity parameter cannot be null");
        }
        mParentActivity = activity;
        //TODO: Can move MsalUtils chrome specific util method to common when refactoring
        mChromePackageWithCustomTabSupport = MsalUtils.getChromePackageWithCustomTabSupport(mParentActivity.getApplicationContext());
    }

    protected void verifyChromeTabOrBrowser() throws MsalClientException {
        if (mChromePackageWithCustomTabSupport == null) {
            Logger.warning(TAG, null, "Custom tab is not supported by Chrome.");

        } else if( MsalUtils.getChromePackage(mParentActivity.getApplicationContext()) == null) {
            Logger.warning(TAG, null, "Chrome is not installed.");
            throw new MsalClientException(ErrorStrings.CHROME_NOT_INSTALLED, "Chrome is not installed.");
        }
    }

    /**
     * Method to bind Chrome {@link android.support.customtabs.CustomTabsService}.
     * Waits until the {@link MsalCustomTabsServiceConnection} is connected or the
     * {@link MsalChromeCustomTabManager#CUSTOM_TABS_MAX_CONNECTION_TIMEOUT} is timed out.
     */
    public synchronized void bindCustomTabsService() {
        if (mChromePackageWithCustomTabSupport != null) {

            final CountDownLatch latch = new CountDownLatch(1);
            mCustomTabsServiceConnection = new MsalCustomTabsServiceConnection(latch);

            // Initiate the service-bind action
            CustomTabsClient.bindCustomTabsService(mParentActivity, mChromePackageWithCustomTabSupport, mCustomTabsServiceConnection);

            boolean customTabsServiceConnected = waitForServiceConnectionToEstablish(latch);

            final CustomTabsIntent.Builder builder = customTabsServiceConnected
                    ? new CustomTabsIntent.Builder(mCustomTabsServiceConnection.getCustomTabsSession()) : new CustomTabsIntent.Builder();

            // Create the Intent used to launch the Url
            mCustomTabsIntent = builder.setShowTitle(true).build();
            mCustomTabsIntent.intent.setPackage(mChromePackageWithCustomTabSupport);
        }
    }

    /**
     * Helper method to wait for MsalCustomTabsServiceConnection to establish.
     */
    private boolean waitForServiceConnectionToEstablish(CountDownLatch latch) {
        boolean connectionEstablished = true;
        try {
            // await returns true if count is 0, false if action times out
            // invert this boolean to indicate if we should skip warming up
            boolean timedOut = !latch.await(CUSTOM_TABS_MAX_CONNECTION_TIMEOUT, TimeUnit.SECONDS);
            if (timedOut) {
                // if the request timed out, we don't actually know whether or not the service connected.
                // to be safe, we'll skip warmup and rely on mCustomTabsServiceIsBound
                // to unbind the Service when onStop() is called.
                connectionEstablished = false;
                Logger.warning(TAG, null, "Connection to CustomTabs timed out. Skipping warmup.");
            }
        } catch (InterruptedException e) {
            Logger.error(TAG, null, "Failed to connect to CustomTabs. Skipping warmup.", e);
            connectionEstablished = false;
        }
        return connectionEstablished;
    }

    /**
     * Method to unbind Chrome {@link android.support.customtabs.CustomTabsService}.
     */
    public synchronized void unbindCustomTabsService() {
        if (null != mCustomTabsServiceConnection && mCustomTabsServiceConnection.getCustomTabsServiceIsBound()) {
            mParentActivity.unbindService(mCustomTabsServiceConnection);
            mCustomTabsServiceConnection.unbindCustomTabsService();
        }
    }

    /**
     * Launches a Chrome Custom tab if available else Chrome Browser for the URL.
     * CustomTabService needs to be bound using {@link MsalChromeCustomTabManager#bindCustomTabsService()}
     * before calling this method.
     *
     * @param requestUrl URL to be loaded.
     */
    public void launchChromeTabOrBrowserForUrl(String requestUrl) {
//        if (mChromePackageWithCustomTabSupport != null && mCustomTabsIntent != null) {
//            Logger.info(TAG, null, "ChromeCustomTab support is available, launching chrome tab.");
//            mCustomTabsIntent.launchUrl(mParentActivity, Uri.parse(requestUrl));
//        } else {
        Logger.info(TAG, null, "Heidi: launch the default browser");
        final List<String> browserList = getBrowserList();
        //Launch the default browser
        final Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(requestUrl));
        Logger.info(TAG, null, "Heidi: launch package" + browserList.get(0).toString());
        browserIntent.setPackage(browserList.get(1));
        browserIntent.addCategory(Intent.CATEGORY_BROWSABLE);
        mParentActivity.startActivity(browserIntent);
//        }
    }

    private List<String> getBrowserList() {
        //get the list of browsers
        final Intent BROWSER_INTENT = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("http://www.example.com"));
        List<String> browserList = new ArrayList<>();
        PackageManager pm = mParentActivity.getPackageManager();
        int queryFlag = PackageManager.GET_RESOLVED_FILTER;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            queryFlag |= PackageManager.MATCH_ALL;
        }
        List<ResolveInfo> resolvedActivityList =
                pm.queryIntentActivities(BROWSER_INTENT, queryFlag);

        for (ResolveInfo info : resolvedActivityList) {
            // ignore handlers which are not browsers
            if (!isFullBrowser(info)) {
                continue;
            }

            try {
                PackageInfo packageInfo = pm.getPackageInfo(
                        info.activityInfo.packageName,
                        PackageManager.GET_SIGNATURES);

                browserList.add(packageInfo.packageName);
            } catch (PackageManager.NameNotFoundException e) {
                // a descriptor cannot be generated without the package info
            }
        }
        Logger.verbose(TAG, null, "Heidi: found " + browserList.size() + " browsers.");
        return browserList;
    }

    private static boolean isFullBrowser(ResolveInfo resolveInfo) {
        // The filter must match ACTION_VIEW, CATEGORY_BROWSEABLE, and at least one scheme,
        if (!resolveInfo.filter.hasAction(Intent.ACTION_VIEW)
                || !resolveInfo.filter.hasCategory(Intent.CATEGORY_BROWSABLE)
                || resolveInfo.filter.schemesIterator() == null) {
            return false;
        }

        // The filter must not be restricted to any particular set of authorities
        if (resolveInfo.filter.authoritiesIterator() != null) {
            return false;
        }

        // The filter must support both HTTP and HTTPS.
        boolean supportsHttp = false;
        boolean supportsHttps = false;
        Iterator<String> schemeIter = resolveInfo.filter.schemesIterator();
        while (schemeIter.hasNext()) {
            String scheme = schemeIter.next();
            supportsHttp |= SCHEME_HTTP.equals(scheme);
            supportsHttps |= SCHEME_HTTPS.equals(scheme);

            if (supportsHttp && supportsHttps) {
                return true;
            }
        }

        // at least one of HTTP or HTTPS is not supported
        return false;
    }

    /**
     * Sub class of CustomTabsServiceConnection to handle lifetime of the
     * CustomTabService connection.
     */
    private static class MsalCustomTabsServiceConnection extends CustomTabsServiceConnection {

        private final WeakReference<CountDownLatch> mLatchWeakReference;
        private CustomTabsClient mCustomTabsClient;
        private CustomTabsSession mCustomTabsSession;
        private boolean mCustomTabsServiceIsBound;

        MsalCustomTabsServiceConnection(final CountDownLatch latch) {
            mLatchWeakReference = new WeakReference<>(latch);
        }

        @Override
        public void onCustomTabsServiceConnected(ComponentName name, CustomTabsClient client) {
            final CountDownLatch latch = mLatchWeakReference.get();

            mCustomTabsServiceIsBound = true;
            mCustomTabsClient = client;
            mCustomTabsClient.warmup(0L);
            mCustomTabsSession = mCustomTabsClient.newSession(null);

            if (null != latch) {
                latch.countDown();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            unbindCustomTabsService();
        }

        /**
         * mCustomTabsServiceIsBound state that will not normally be handled by garbage collection.
         * This should be called when the authorization service is no longer required, including
         * when any owning activity is paused or destroyed (i.e. in {@link android.app.Activity#onStop()}).
         */
        private void unbindCustomTabsService() {
            mCustomTabsClient = null;
            mCustomTabsServiceIsBound = false;
        }

        /**
         * Gets the {@link CustomTabsSession} associated to this CustomTabs connection.
         *
         * @return the session.
         */
        CustomTabsSession getCustomTabsSession() {
            return mCustomTabsSession;
        }

        /**
         * Boolean to indicate if the {@link android.support.customtabs.CustomTabsService} is bound or not.
         *
         * @return true if the CustomTabsService is bound else false.
         */
        boolean getCustomTabsServiceIsBound() {
            return mCustomTabsServiceIsBound;
        }
    }
}
