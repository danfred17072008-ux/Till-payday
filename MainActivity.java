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
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        webView.setBackgroundColor(0xFFF4F2ED);
        webView.addJavascriptInterface(new BillingBridge(), "TillPaydayBilling");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");

        billingClient = BillingClient.newBuilder(this)
                .setListener(this)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .enableAutoServiceReconnection()
                .build();

        connectBilling();
    }

    private void connectBilling() {
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    restorePurchase();
                    loadProduct();
                } else {
                    sendBillingStatus("Google Play billing isn't ready yet.", false, "");
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // Auto service reconnection is enabled. No manual reconnect loop needed.
            }
        });
    }

    private void loadProduct() {
        QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build();

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(product))
                .build();

        billingClient.queryProductDetailsAsync(params, (billingResult, result) -> {
            if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                sendBillingStatus("Couldn't load the purchase from Google Play.", unlocked, "");
                return;
            }

            List<ProductDetails> products = result.getProductDetailsList();
            if (products == null || products.isEmpty()) {
                sendBillingStatus("Purchase isn't active in Play Console yet.", unlocked, "");
                return;
            }

            unlockProduct = products.get(0);
            String price = getDisplayPrice(unlockProduct);
            sendBillingStatus(unlocked ? "Full version unlocked." : "One-time purchase. No subscription.", unlocked, price);
        });
    }

    private String getDisplayPrice(ProductDetails details) {
        List<ProductDetails.OneTimePurchaseOfferDetails> offers = details.getOneTimePurchaseOfferDetailsList();
        if (offers != null && !offers.isEmpty()) {
            return offers.get(0).getFormattedPrice();
        }
        return "";
    }

    private ProductDetails.OneTimePurchaseOfferDetails getOffer(ProductDetails details) {
        List<ProductDetails.OneTimePurchaseOfferDetails> offers = details.getOneTimePurchaseOfferDetailsList();
        return (offers == null || offers.isEmpty()) ? null : offers.get(0);
    }

    private void launchPurchase() {
        if (!billingClient.isReady()) {
            sendBillingStatus("Google Play billing is connecting. Try again in a moment.", unlocked, "");
            connectBilling();
            return;
        }
        if (unlockProduct == null) {
            loadProduct();
            sendBillingStatus("Loading the purchase from Google Play…", unlocked, "");
            return;
        }

        ProductDetails.OneTimePurchaseOfferDetails offer = getOffer(unlockProduct);
        if (offer == null) {
            sendBillingStatus("No purchase option is active in Play Console.", unlocked, "");
            return;
        }

        BillingFlowParams.ProductDetailsParams productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(unlockProduct)
                .setOfferToken(offer.getOfferToken())
                .build();

        BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(productParams))
                .build();

        BillingResult result = billingClient.launchBillingFlow(this, flowParams);
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            sendBillingStatus("Google Play couldn't start the purchase.", unlocked, getDisplayPrice(unlockProduct));
        }
    }

    private void restorePurchase() {
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build();

        billingClient.queryPurchasesAsync(params, (billingResult, purchases) -> {
            if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) return;
            for (Purchase purchase : purchases) {
                if (purchase.getProducts().contains(PRODUCT_ID)
                        && purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    grantAndAcknowledge(purchase);
                    return;
                }
            }
            unlocked = false;
            sendBillingStatus("One-time purchase. No subscription.", false,
                    unlockProduct == null ? "" : getDisplayPrice(unlockProduct));
        });
    }

    private void grantAndAcknowledge(Purchase purchase) {
        unlocked = true;
        sendBillingStatus("Full version unlocked.", true,
                unlockProduct == null ? "" : getDisplayPrice(unlockProduct));

        if (!purchase.isAcknowledged()) {
            AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.getPurchaseToken())
                    .build();
            billingClient.acknowledgePurchase(params, billingResult -> { });
        }
    }

    @Override
    public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                if (purchase.getProducts().contains(PRODUCT_ID)
                        && purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    grantAndAcknowledge(purchase);
                } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                    sendBillingStatus("Purchase pending — Google Play will unlock it when payment completes.", false,
                            unlockProduct == null ? "" : getDisplayPrice(unlockProduct));
                }
            }
        } else if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.USER_CANCELED) {
            sendBillingStatus("Purchase didn't complete. You haven't been charged by this screen.", unlocked,
                    unlockProduct == null ? "" : getDisplayPrice(unlockProduct));
        }
    }

    private void sendBillingStatus(String message, boolean isUnlocked, String price) {
        String js = "window.billingUpdate && window.billingUpdate(" +
                quoteJs(message) + "," + isUnlocked + "," + quoteJs(price) + ");";
        runOnUiThread(() -> webView.evaluateJavascript(js, null));
    }

    private String quoteJs(String value) {
        if (value == null) value = "";
        return "\"" + value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "") + "\"";
    }

    public class BillingBridge {
        @JavascriptInterface
        public void buyFullVersion() {
            runOnUiThread(() -> launchPurchase());
        }

        @JavascriptInterface
        public void restorePurchases() {
            runOnUiThread(() -> restorePurchase());
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (billingClient != null) billingClient.endConnection();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
