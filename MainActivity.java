package com.tillpayday.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryProductDetailsResult;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity implements PurchasesUpdatedListener {

    private static final String PRODUCT_ID = "till_payday_full_unlock";

    private WebView webView;
    private BillingClient billingClient;
    private ProductDetails unlockProduct;
    private boolean unlocked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        webView.addJavascriptInterface(
                new AndroidBridge(),
                "AndroidBilling"
        );

        webView.loadUrl("file:///android_asset/index.html");

        setupBilling();
    }

    private void setupBilling() {

        PendingPurchasesParams pendingPurchasesParams =
                PendingPurchasesParams.newBuilder()
                        .enableOneTimeProducts()
                        .build();

        billingClient = BillingClient.newBuilder(this)
                .setListener(this)
                .enablePendingPurchases(pendingPurchasesParams)
                .build();

        billingClient.startConnection(
                new BillingClientStateListener() {

                    @Override
                    public void onBillingSetupFinished(
                            BillingResult billingResult) {

                        if (billingResult.getResponseCode()
                                == BillingClient.BillingResponseCode.OK) {

                            queryProduct();
                            checkExistingPurchases();
                        }
                    }

                    @Override
                    public void onBillingServiceDisconnected() {
                        // Billing reconnects when required.
                    }
                }
        );
    }

    private void queryProduct() {

        QueryProductDetailsParams.Product product =
                QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(
                                BillingClient.ProductType.INAPP
                        )
                        .build();

        QueryProductDetailsParams params =
                QueryProductDetailsParams.newBuilder()
                        .setProductList(
                                Collections.singletonList(product)
                        )
                        .build();

        billingClient.queryProductDetailsAsync(
                params,
                (BillingResult billingResult,
                 QueryProductDetailsResult result) -> {

                    List<ProductDetails> products =
                            result.getProductDetailsList();

                    if (billingResult.getResponseCode()
                            == BillingClient.BillingResponseCode.OK
                            && products != null
                            && !products.isEmpty()) {

                        unlockProduct = products.get(0);

                        String price = "";

                        if (unlockProduct
                                .getOneTimePurchaseOfferDetails()
                                != null) {

                            price = unlockProduct
                                    .getOneTimePurchaseOfferDetails()
                                    .getFormattedPrice();
                        }

                        final String finalPrice = price;

                        runOnUiThread(() ->
                                webView.evaluateJavascript(
                                        "if(window.setUpgradePrice){" +
                                                "window.setUpgradePrice('" +
                                                escapeJs(finalPrice) +
                                                "');}",
                                        null
                                )
                        );
                    }
                }
        );
    }

    private void checkExistingPurchases() {

        QueryPurchasesParams params =
                QueryPurchasesParams.newBuilder()
                        .setProductType(
                                BillingClient.ProductType.INAPP
                        )
                        .build();

        billingClient.queryPurchasesAsync(
                params,
                (billingResult, purchases) -> {

                    if (billingResult.getResponseCode()
                            == BillingClient.BillingResponseCode.OK) {

                        handlePurchases(purchases);
                    }
                }
        );
    }

    private void handlePurchases(List<Purchase> purchases) {

        if (purchases == null) {
            return;
        }

        for (Purchase purchase : purchases) {

            if (purchase.getProducts().contains(PRODUCT_ID)
                    && purchase.getPurchaseState()
                    == Purchase.PurchaseState.PURCHASED) {

                if (!purchase.isAcknowledged()) {

                    AcknowledgePurchaseParams acknowledgeParams =
                            AcknowledgePurchaseParams.newBuilder()
                                    .setPurchaseToken(
                                            purchase.getPurchaseToken()
                                    )
                                    .build();

                    billingClient.acknowledgePurchase(
                            acknowledgeParams,
                            billingResult -> {

                                if (billingResult.getResponseCode()
                                        == BillingClient
                                        .BillingResponseCode.OK) {

                                    unlockFullVersion();
                                }
                            }
                    );

                } else {

                    unlockFullVersion();
                }
            }
        }
    }

    private void unlockFullVersion() {

        unlocked = true;

        runOnUiThread(() ->
                webView.evaluateJavascript(
                        "if(window.unlockTillPayday){" +
                                "window.unlockTillPayday();" +
                                "}",
                        null
                )
        );
    }

    private void startPurchase() {

        if (billingClient == null
                || !billingClient.isReady()) {
            setupBilling();
            return;
        }

        if (unlockProduct == null) {
            queryProduct();
            return;
        }

        BillingFlowParams.ProductDetailsParams productParams =
                BillingFlowParams.ProductDetailsParams
                        .newBuilder()
                        .setProductDetails(unlockProduct)
                        .build();

        BillingFlowParams flowParams =
                BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(
                                Collections.singletonList(
                                        productParams
                                )
                        )
                        .build();

        billingClient.launchBillingFlow(
                this,
                flowParams
        );
    }

    @Override
    public void onPurchasesUpdated(
            BillingResult billingResult,
            List<Purchase> purchases) {

        if (billingResult.getResponseCode()
                == BillingClient.BillingResponseCode.OK
                && purchases != null) {

            handlePurchases(purchases);

        } else if (billingResult.getResponseCode()
                == BillingClient
                .BillingResponseCode.USER_CANCELED) {

            // User cancelled. Nothing to do.

        } else {

            runOnUiThread(() ->
                    webView.evaluateJavascript(
                            "if(window.purchaseError){" +
                                    "window.purchaseError();" +
                                    "}",
                            null
                    )
            );
        }
    }

    private String escapeJs(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'");
    }

    public class AndroidBridge {

        @JavascriptInterface
        public void buyFullVersion() {

            runOnUiThread(() ->
                    startPurchase()
            );
        }

        @JavascriptInterface
        public boolean isFullVersionUnlocked() {
            return unlocked;
        }

        @JavascriptInterface
        public void restorePurchase() {

            runOnUiThread(() ->
                    checkExistingPurchases()
            );
        }
    }

    @Override
    protected void onDestroy() {

        if (billingClient != null) {
            billingClient.endConnection();
        }

        if (webView != null) {
            webView.destroy();
        }

        super.onDestroy();
    }
}
