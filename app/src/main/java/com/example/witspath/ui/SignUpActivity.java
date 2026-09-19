package com.example.witspath.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.witspath.ui.BaseActivity;

import com.example.witspath.R;
import com.example.witspath.util.Prefs;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SignUpActivity extends BaseActivity {

    private TextInputEditText nameInput;
    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private TextInputEditText confirmPasswordInput;
    private ChipGroup mobilityChipGroup;
    private MaterialCheckBox termsCheckBox;
    private MaterialButton signUpButton;
    private TextView errorText;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        mAuth = FirebaseAuth.getInstance();

        nameInput = findViewById(R.id.signUpNameInput);
        emailInput = findViewById(R.id.signUpEmailInput);
        passwordInput = findViewById(R.id.signUpPasswordInput);
        confirmPasswordInput = findViewById(R.id.signUpConfirmPasswordInput);
        mobilityChipGroup = findViewById(R.id.signUpMobilityChipGroup);
        termsCheckBox = findViewById(R.id.signUpTermsCheckBox);
        signUpButton = findViewById(R.id.signUpButton);
        errorText = findViewById(R.id.signUpErrorText);
        progressBar = findViewById(R.id.signUpProgressBar);

        MaterialToolbar toolbar = findViewById(R.id.signUpToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        signUpButton.setOnClickListener(v -> handleSignUp());
        findViewById(R.id.signUpGoToLogInText).setOnClickListener(v -> finish());
    }

    private void handleSignUp() {
        String name = nameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        String confirmPassword = confirmPasswordInput.getText().toString();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            showError(getString(R.string.signup_error_empty));
            return;
        }

        if (!password.equals(confirmPassword)) {
            showError(getString(R.string.signup_error_password_mismatch));
            return;
        }

        if (!termsCheckBox.isChecked()) {
            showError(getString(R.string.signup_error_terms));
            return;
        }

        setLoading(true);
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        updateProfileAndSync(name);
                    } else {
                        setLoading(false);
                        showError(getString(R.string.signup_error_generic));
                    }
                });
    }

    private void updateProfileAndSync(String name) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            setLoading(false);
            showError(getString(R.string.signup_error_generic));
            return;
        }

        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build();

        user.updateProfile(profileUpdates).addOnCompleteListener(task -> {
            int checkedId = mobilityChipGroup.getCheckedChipId();
            String profile = "none";
            if (checkedId == R.id.signUpWheelchairChip) profile = "wheelchair";
            else if (checkedId == R.id.signUpWalkingAidChip) profile = "walking_aid";
            else if (checkedId == R.id.signUpLowVisionChip) profile = "low_vision";

            Prefs prefs = new Prefs(this);
            prefs.setString(Prefs.KEY_MOBILITY_PROFILE, profile);

            Map<String, Object> userData = new HashMap<>();
            userData.put("displayName", name);
            userData.put("email", user.getEmail());
            
            Map<String, Object> p = new HashMap<>();
            p.put(Prefs.KEY_MOBILITY_PROFILE, profile);
            userData.put("preferences", p);

            FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                    .set(userData)
                    .addOnCompleteListener(t -> {
                        setLoading(false);
                        startActivity(new Intent(this, HomeActivity.class));
                        finishAffinity();
                    });
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        signUpButton.setEnabled(!loading);
        errorText.setVisibility(View.GONE);
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }
}
