package org.gooddollar.facetec.processors;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.facetec.sampleapp.SampleAppActivity;
import com.facetec.sampleapp.StartPageActivity;
import com.facetec.sdk.FaceTecCustomization;
import com.facetec.sdk.FaceTecFaceScanProcessor;
import com.facetec.sdk.FaceTecFaceScanResultCallback;
import com.facetec.sdk.FaceTecIDScanProcessor;
import com.facetec.sdk.FaceTecIDScanResult;
import com.facetec.sdk.FaceTecIDScanResultCallback;
import com.facetec.sdk.FaceTecIDScanRetryMode;
import com.facetec.sdk.FaceTecIDScanStatus;
import com.facetec.sdk.FaceTecSDK;
import com.facetec.sdk.FaceTecSessionActivity;
import com.facetec.sdk.FaceTecSessionResult;
import com.facetec.sdk.FaceTecSessionStatus;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.RequestBody;

// Android Note 1:  Some commented "Parts" below are out of order so that they can match iOS and Browser source for this same file on those platforms.
// Android Note 2:  Android does not have a onFaceTecSDKCompletelyDone function that you must implement like "Part 10" of iOS and Android Samples.  Instead, onActivityResult is used as the place in code you get control back from the FaceTec SDK.
public class PhotoIDMatchProcessor implements FaceTecFaceScanProcessor, FaceTecIDScanProcessor {
    private boolean isSuccess = false;
    private Context context;
    private ProcessingSubscriber subscriber;
    public String sessionRef;

    public PhotoIDMatchProcessor(Context context, ProcessingSubscriber subscriber) {
      this.context = context;
      this.subscriber = subscriber;
    }

    public ProcessingSubscriber getSubscriber() {
      return subscriber;
    }

    //
    // Handling the Result of a FaceScan
    //
    public void processSessionWhileFaceTecSDKWaits(final FaceTecSessionResult sessionResult, final FaceTecFaceScanResultCallback faceScanResultCallback) {
        if(sessionResult.getStatus() != FaceTecSessionStatus.SESSION_COMPLETED_SUCCESSFULLY) {
            NetworkingHelpers.cancelPendingRequests();
            faceScanResultCallback.cancel();
            return;
        }

        //
        // Part 4:  Get essential data off the FaceTecSessionResult
        //
        JSONObject parameters = new JSONObject();
        try {
            parameters.put("faceScan", sessionResult.getFaceScanBase64());
            parameters.put("auditTrailImage", sessionResult.getAuditTrailCompressedBase64()[0]);
            parameters.put("lowQualityAuditTrailImage", sessionResult.getLowQualityAuditTrailCompressedBase64()[0]);
            parameters.put("externalDatabaseRefID", this.sessionRef);
        }
        catch(JSONException e) {
            e.printStackTrace();
            Log.d("FaceTecSDKSampleApp", "Exception raised while attempting to create JSON payload for upload.");
        }

        //
        // Part 5:  Make the Networking Call to Your Servers.  Below is just example code, you are free to customize based on how your own API works.
        //
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(Config.BaseURL + "/enrollment-3d")
                .header("Content-Type", "application/json")
                .header("X-Device-Key", Config.DeviceKeyIdentifier)
                .header("User-Agent", FaceTecSDK.createFaceTecAPIUserAgentString(sessionResult.getSessionId()))

                //
                // Part 7:  Demonstrates updating the Progress Bar based on the progress event.
                //
                .post(new ProgressRequestBody(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), parameters.toString()),
                        new ProgressRequestBody.Listener() {
                            @Override
                            public void onUploadProgressChanged(long bytesWritten, long totalBytes) {
                                final float uploadProgressPercent = ((float)bytesWritten) / ((float)totalBytes);
                                faceScanResultCallback.uploadProgress(uploadProgressPercent);
                            }
                        }))
                .build();

        //
        // Part 8:  Actually send the request.
        //
        NetworkingHelpers.getApiClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, okhttp3.Response response) throws IOException {
                //
                // Part 6:  In our Sample, we evaluate a boolean response and treat true as success, false as "User Needs to Retry",
                // and handle all other non-nominal responses by cancelling out.  You may have different paradigms in your own API and are free to customize based on these.
                //
                String responseString = response.body().string();
                response.body().close();
                try {
                    JSONObject responseJSON = new JSONObject(responseString);

                    //
                    // DEVELOPER NOTE:  These properties are for demonstration purposes only so the Sample App can get information about what is happening in the processor.
                    // In the code in your own App, you can pass around signals, flags, intermediates, and results however you would like.
                    //
                    boolean didSucceed = responseJSON.getBoolean("success");



                    if (didSucceed == true) {
                        // CASE:  Success!  The Enrollment was performed and the User successfully enrolled.

                        // Demonstrates dynamically setting the Success Screen Message.
                        //FaceTecCustomization.overrideResultScreenSuccessMessage = responseString;
                        FaceTecCustomization.overrideResultScreenSuccessMessage = "Liveness\nConfirmed";

                        faceScanResultCallback.succeed();
                    }
                    else if (didSucceed == false) {
                        // CASE:  In our Sample code, "success" being present and false means that the User Needs to Retry.
                        // Real Users will likely succeed on subsequent attempts after following on-screen guidance.
                        // Attackers/Fraudsters will continue to get rejected.
                        faceScanResultCallback.retry();
                    }
                    else {
                        // CASE:  UNEXPECTED response from API.  Our Sample Code keys of a success boolean on the root of the JSON object --> You define your own API contracts with yourself and may choose to do something different here based on the error.
                        faceScanResultCallback.cancel();
                    }
                }
                catch(JSONException e) {
                    // CASE:  Parsing the response into JSON failed --> You define your own API contracts with yourself and may choose to do something different here based on the error.  Solid server-side code should ensure you don't get to this case.
                    e.printStackTrace();
                    Log.d("FaceTecSDKSampleApp", "Exception raised while attempting to parse JSON result.");
                    faceScanResultCallback.cancel();
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                // CASE:  Network Request itself is erroring --> You define your own API contracts with yourself and may choose to do something different here based on the error.
                Log.d("FaceTecSDKSampleApp", "Exception raised while attempting HTTPS call.");
                faceScanResultCallback.cancel();
            }
        });

        //
        // Part 9:  For better UX, update the User if the upload is taking a while.  You are free to customize and enhance this behavior to your liking.
        //
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if(faceScanResultCallback == null) { return; }
                faceScanResultCallback.uploadMessageOverride("Still Uploading...");
            }
        }, 6000);
    }

    //
    // Part 1:  Handling the Result of an IDScan
    //
    public void processIDScanWhileFaceTecSDKWaits(final FaceTecIDScanResult idScanResult, final FaceTecIDScanResultCallback idScanResultCallback) {
        //
        // DEVELOPER NOTE:  These properties are for demonstration purposes only so the Sample App can get information about what is happening in the processor.
        // In the code in your own App, you can pass around signals, flags, intermediates, and results however you would like.
        //
        //sampleAppActivity.setLatestIDScanResult(idScanResult);

        //
        // Part 2:  Handles early exit scenarios where there is no IDScan to handle -- i.e. User Cancellation, Timeouts, etc.
        //
        if(idScanResult.getStatus() != FaceTecIDScanStatus.SUCCESS) {
            NetworkingHelpers.cancelPendingRequests();
            idScanResultCallback.cancel();
            return;
        }

        // IMPORTANT:  FaceTecSDK.FaceTecIDScanStatus.Success DOES NOT mean the IDScan 3d-2d Matching was Successful.
        // It simply means the User completed the Session and a 3D FaceScan was created. You still need to perform the IDScan 3d-2d Matching on your Servers.

        //
        // minMatchLevel allows Developers to specify a Match Level that they would like to target in order for success to be true in the response.
        // minMatchLevel cannot be set to 0.
        // minMatchLevel setting does not affect underlying Algorithm behavior.
        final int minMatchLevel = 3;

        //
        // Part 3: Get essential data off the FaceTecIDScanResult
        //
        //this.sampleAppActivity.LogMessage("Uploading photos");
        JSONObject parameters = new JSONObject();
        ArrayList<String> frontImagesCompressedBase64 = idScanResult.getFrontImagesCompressedBase64();
        ArrayList<String> backImagesCompressedBase64 = idScanResult.getBackImagesCompressedBase64();

        final String idBackPhoto = backImagesCompressedBase64.get(0);
        try {
            parameters.put("externalDatabaseRefID", this.sessionRef);
            parameters.put("idScan", idScanResult.getIDScanBase64());
            parameters.put("minMatchLevel", minMatchLevel);


            if(frontImagesCompressedBase64.size() > 0) {
                parameters.put("idScanFrontImage", frontImagesCompressedBase64.get(0));
            }
            if(backImagesCompressedBase64.size() > 0) {
                parameters.put("idScanBackImage", backImagesCompressedBase64.get(0));
            }
        }
        catch(JSONException e) {
            e.printStackTrace();
            Log.d("FaceTecSDKSampleApp", "Exception raised while attempting to create JSON payload for upload.");
        }

        //
        // Part 4:  Make the Networking Call to Your Servers.  Below is just example code, you are free to customize based on how your own API works.
        //
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(Config.BaseURL + "/match-3d-2d-idscan")
                .header("Content-Type", "application/json")
                .header("X-Device-Key", Config.DeviceKeyIdentifier)
                .header("User-Agent", FaceTecSDK.createFaceTecAPIUserAgentString(idScanResult.getSessionId()))

                //
                // Part 6:  Demonstrates updating the Progress Bar based on the progress event.
                //
                .post(new ProgressRequestBody(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), parameters.toString()),
                        new ProgressRequestBody.Listener() {
                            @Override
                            public void onUploadProgressChanged(long bytesWritten, long totalBytes) {
                                final float uploadProgressPercent = ((float)bytesWritten) / ((float)totalBytes);
                                idScanResultCallback.uploadProgress(uploadProgressPercent);
                            }
                        }))
                .build();

        //
        // Part 7:  Actually send the request.
        //


        NetworkingHelpers.getApiClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, okhttp3.Response response) throws IOException {
                //
                // Part 17:  In our Sample, we evaluate a boolean response and treat true as success, false as "User Needs to Retry",
                // and handle all other non-nominal responses by cancelling out.  You may have different paradigms in your own API and are free to customize based on these.
                //
                String responseString = response.body().string();
                response.body().close();
                try {
                    JSONObject responseJSON = new JSONObject(responseString);

                    //
                    // DEVELOPER NOTE:  These properties are for demonstration purposes only so the Sample App can get information about what is happening in the processor.
                    // In the code in your own App, you can pass around signals, flags, intermediates, and results however you would like.
                    //
                    //sampleAppActivity.setLatestServerResult(responseJSON);

                    boolean didSucceed = responseJSON.getBoolean("success");
                    int fullIDStatusEnumInt = responseJSON.getInt("fullIDStatusEnumInt");
                    int digitalIDSpoofStatusEnumInt = responseJSON.getInt("digitalIDSpoofStatusEnumInt");
                    int matchLevel = responseJSON.getInt("matchLevel");


                    if (didSucceed == true) {

                        FaceTecCustomization.overrideResultScreenSuccessMessage = "Facial recognition and document verification passed with "+MatchLevel.GetLevel(matchLevel)+" match!";
                        idScanResultCallback.succeed();

                        PhotoIDMatchProcessor.this.parentActivity.onFaceTecComplete(idBackPhoto);
                        //activity.LogMessage("ID looks good.");
                        // CASE:  Success!  The ID Match was performed and the User successfully matched.

                        //
                        // DEVELOPER NOTE:  These properties are for demonstration purposes only so the Sample App can get information about what is happening in the processor.
                        // In the code in your own App, you can pass around signals, flags, intermediates, and results however you would like.
                        //
                    }
                    else if (didSucceed == false) {
                        //activity.LogMessage("ID does not look good.");
                        // CASE:  In our Sample code, "success" being present and false means that the User Needs to Retry.
                        // Real Users will likely succeed on subsequent attempts after following on-screen guidance.
                        // Attackers/Fraudsters will continue to get rejected.

                        // Handle invalid ID by displaying custom message
                        // If we could not determine the ID was Fully Visible and that the ID was a Physical, alter the feedback message to the User.
                        if(fullIDStatusEnumInt == 1 || digitalIDSpoofStatusEnumInt == 1) {
                            idScanResultCallback.retry(FaceTecIDScanRetryMode.FRONT, "Photo ID\nNot Fully Visible");
                        }
                        else {
                            idScanResultCallback.retry(FaceTecIDScanRetryMode.FRONT);
                        }
                    }
                    else {
                        //activity.LogMessage("Other error.");
                        // CASE:  UNEXPECTED response from API.  Our Sample Code keys of a success boolean on the root of the JSON object --> You define your own API contracts with yourself and may choose to do something different here based on the error.
                        idScanResultCallback.cancel();
                    }
                }
                catch(JSONException e) {
                    // CASE:  Parsing the response into JSON failed --> You define your own API contracts with yourself and may choose to do something different here based on the error.  Solid server-side code should ensure you don't get to this case.
                    e.printStackTrace();
                    //activity.LogMessage("Exception: "+e.getMessage()+"Stack: "+e.getStackTrace());
                    Log.d("FaceTecSDKSampleApp", "Exception raised while attempting to parse JSON result.");
                    idScanResultCallback.cancel();
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                // CASE:  Network Request itself is erroring --> You define your own API contracts with yourself and may choose to do something different here based on the error.
                Log.d("FaceTecSDKSampleApp", "Exception raised while attempting HTTPS call.");
                idScanResultCallback.cancel();
            }
        });

        //
        // Part 8:  For better UX, update the User if the upload is taking a while.  You are free to customize and enhance this behavior to your liking.
        //
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if(idScanResultCallback == null) { return; }
                idScanResultCallback.uploadMessageOverride("Still Uploading...");
            }
        }, 6000);
    }

    public boolean isSuccess() {
        return this.isSuccess;
    }
}
