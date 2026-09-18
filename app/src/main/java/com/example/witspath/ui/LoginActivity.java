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
import com.example.witspath.model.FirestoreSyncManager;
import com.example.witspath.util.Prefs;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Team Wavelets - WitsPath
 * Handles Firebase Auth sign-in flow (email/password and guest).
 */
public class LoginActivity extends BaseActivity {

    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private MaterialButton loginButton;
    private MaterialButton guestButton;
    private TextView errorText;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        emailInput = findViewById(R.id.loginEmailInput);
        passwordInput = findViewById(R.id.loginPasswordInput);
        loginButton = findViewById(R.id.loginButton);
        guestButton = findViewById(R.id.loginGuestButton);
        errorText = findViewById(R.id.loginErrorText);
        progressBar = findViewById(R.id.loginProgressBar);

        MaterialToolbar toolbar = findViewById(R.id.loginToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        loginButton.setOnClickListener(v -> signInWithEmail());
        guestButton.setOnClickListener(v -> signInAnonymously());

        findViewById(R.id.loginGoToSignUpText).setOnClickListener(v -> {
            startActivity(new Intent(this, SignUpActivity.class));
            finish();
        });

        findViewById(R.id.loginForgotPasswordText).setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            if (TextUtils.isEmpty(email)) {
                Toast.makeText(this, R.string.login_error_generic, Toast.LENGTH_SHORT).show();
                return;
            }
            mAuth.sendPasswordResetEmail(email).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Toast.makeText(this, R.string.login_reset_sent, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void signInWithEmail() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            showError(getString(R.string.login_error_empty));
            return;
        }

        setLoading(true);
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        onAuthSuccess();
                    } else {
                        showError(task.getException() != null ? task.getException().getMessage() : getString(R.string.login_error_generic));
                    }
                });
    }

    private void signInAnonymously() {
        setLoading(true);
        mAuth.signInAnonymously()
                .addOnCompleteListener(this, task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        onAuthSuccess();
                    } else {
                        showError(getString(R.string.login_error_guest_failed));
                    }
                });
    }

    private void onAuthSuccess() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            new FirestoreSyncManager().pushPreferencesToFirestore(
                    user, new Prefs(this));
        }
        Toast.makeText(this, R.string.login_success, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!loading);
        guestButton.setEnabled(!loading);
        errorText.setVisibility(View.GONE);
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }
}
