/*
 * Copyright (c) 2016-2017 Projekt Substratum
 * This file is part of Substratum.
 *
 * Substratum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Substratum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Substratum.  If not, see <http://www.gnu.org/licenses/>.
 */

package projekt.substratum.util.injectors;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Arrays;

import projekt.substratum.R;
import projekt.substratum.common.References;

public class AOPTCheck {

    private Context mContext;
    private SharedPreferences prefs;
    private String aoptPath;

    @SuppressWarnings("EqualsBetweenInconvertibleTypes")
    public void injectAOPT(Context context, Boolean forced) {
        mContext = context;

        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        aoptPath = context.getFilesDir().getAbsolutePath() + "/aopt";

        // Check if AOPT is installed on the device
        File aopt = new File(aoptPath);
        if (!aopt.isFile() || forced) {
            inject();
        } else if (aopt.exists()) {
            String integrityCheck = checkAOPTIntegrity();
            // AOPT outputs different ui
            if (integrityCheck != null &&
                    (integrityCheck.equals(mContext.getString(R.string.aapt_version)) ||
                            integrityCheck.equals(mContext.getString(R.string.aopt_version)))) {
                Log.d(References.SUBSTRATUM_LOG,
                        "The system partition already contains an existing compiler " +
                                "and Substratum is locked and loaded!");
            } else {
                Log.e(References.SUBSTRATUM_LOG,
                        "The system partition already contains an existing compiler, " +
                                "however it does not match Substratum integrity.");
                inject();
            }
        }
    }

    private void inject() {
        if (!Arrays.toString(Build.SUPPORTED_ABIS).contains("86")) {
            // Developers: AOPT-ARM (32bit) is using the legacy AAPT binary, while AAPT-ARM64
            //             (64bit) is using the brand new AOPT binary.
            String architecture =
                    Arrays.asList(Build.SUPPORTED_64_BIT_ABIS).size() > 0 ? "ARM64" : "ARM";
            try {
                if (prefs.getString("compiler", "aopt").equals("aopt")) {
                    copyAOPT("aopt" + (architecture.equals("ARM64") ? "64" : ""));
                    Log.d(References.SUBSTRATUM_LOG,
                            "Android Overlay Packaging Tool (" + architecture + ") " +
                                    "has been added into the compiler directory.");
                } else {
                    copyAOPT("aapt");
                    Log.d(References.SUBSTRATUM_LOG,
                            "Android Asset Packaging Tool (" + architecture + ") " +
                                    "has been added into the compiler directory.");
                }
            } catch (Exception e) {
                // Suppress warning
            }
        } else {
            // Take account for x86 devices
            try {
                copyAOPT("aapt86");
                Log.d(References.SUBSTRATUM_LOG,
                        "Android Asset Packaging Tool (x86) " +
                                "has been added into the compiler directory.");
            } catch (Exception e) {
                // Suppress warning
            }
        }
    }

    private boolean copyAOPT(String filename) {
        AssetManager assetManager = mContext.getAssets();
        boolean res = true;
        try (InputStream in = assetManager.open(filename);
             OutputStream out = new FileOutputStream(aoptPath)) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } catch (Exception e) {
            e.printStackTrace();
            res = false;
        }
        if (res) {
            File f = new File(aoptPath);
            if (f.isFile()) {
                res = f.setExecutable(true, true);
            }
        }
        return res;
    }

    private String checkAOPTIntegrity() {
        Process proc = null;
        try {
            Runtime rt = Runtime.getRuntime();
            proc = rt.exec(new String[]{aoptPath, "version"});
            try (BufferedReader stdInput = new BufferedReader(new
                    InputStreamReader(proc.getInputStream()))) {
                return stdInput.readLine();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (proc != null) {
                proc.destroy();
            }
        }
        return null;
    }
}
