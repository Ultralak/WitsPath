package com.example.witspath.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.witspath.R;
import com.example.witspath.model.ReportEntry;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class MyReportsActivity extends BaseActivity {

    private RecyclerView recyclerView;
    private View emptyText;
    private View signInPromptText;
    private View errorText;
    private ReportsAdapter adapter;
    private final List<ReportEntry> reportsList = new ArrayList<>();
    private final Map<String, String> edgeStatusesMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_reports);

        MaterialToolbar toolbar = findViewById(R.id.myReportsToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.myReportsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        emptyText = findViewById(R.id.myReportsEmptyText);
        signInPromptText = findViewById(R.id.myReportsSignInPromptText);
        errorText = findViewById(R.id.myReportsErrorText);

        ImageButton btnAddReport = findViewById(R.id.btnAddReport);
        if (btnAddReport != null) {
            btnAddReport.setOnClickListener(v -> onAddReportClicked());
        }
    }

    private void onAddReportClicked() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous()) {
            Toast.makeText(this, R.string.my_reports_sign_in_prompt, Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        showReportSheet(user.getUid());
    }

    private void showReportSheet(String userId) {
        BottomSheetDialog sheetDialog =
                new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.sheet_report_issue, null);
        sheetDialog.setContentView(view);

        RadioGroup radioGroup = view.findViewById(R.id.issueTypeRadioGroup);
        View submitBtn = view.findViewById(R.id.submitReportButton);

        if (radioGroup != null && submitBtn != null) {
            submitBtn.setEnabled(radioGroup.getCheckedRadioButtonId() != -1);
            radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
                submitBtn.setEnabled(checkedId != -1);
            });

            submitBtn.setOnClickListener(v -> {
                int checkedId = radioGroup.getCheckedRadioButtonId();
                if (checkedId == -1) return;

                String issueType = "other";
                if (checkedId == R.id.issueBrokenLiftRadio) {
                    issueType = "broken_lift";
                } else if (checkedId == R.id.issueBlockedRampRadio) {
                    issueType = "blocked_or_broken_ramp";
                } else if (checkedId == R.id.issuePathObstructedRadio) {
                    issueType = "path_obstructed";
                } else if (checkedId == R.id.issueOtherRadio) {
                    issueType = "other";
                }

                Map<String, Object> report = new HashMap<>();
                report.put("userId", userId);
                report.put("issueType", issueType);
                report.put("source", "android-app");
                report.put("timestamp", FieldValue.serverTimestamp());

                submitBtn.setEnabled(false);
                FirebaseFirestore.getInstance().collection("reports").add(report)
                        .addOnSuccessListener(doc -> {
                            sheetDialog.dismiss();
                            Toast.makeText(MyReportsActivity.this, R.string.report_success, Toast.LENGTH_SHORT).show();
                            loadReports();
                        })
                        .addOnFailureListener(e -> {
                            submitBtn.setEnabled(true);
                            Toast.makeText(MyReportsActivity.this, R.string.report_failed, Toast.LENGTH_SHORT).show();
                        });
            });
        }

        sheetDialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadReports();
    }

    private void loadReports() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous()) {
            showSignInPrompt();
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("reports")
                .whereEqualTo("userId", user.getUid())
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(this::onReportsLoaded)
                .addOnFailureListener(e -> {
                    Log.e("MyReportsActivity", "Error querying reports from Firestore", e);
                    showErrorState();
                });
    }

    private void onReportsLoaded(QuerySnapshot snapshot) {
        reportsList.clear();
        Set<String> edgeIds = new HashSet<>();
        for (QueryDocumentSnapshot doc : snapshot) {
            ReportEntry entry = doc.toObject(ReportEntry.class);
            entry.setReportId(doc.getId());
            reportsList.add(entry);
            if (entry.getEdgeId() != null) {
                edgeIds.add(entry.getEdgeId());
            }
        }

        if (edgeIds.isEmpty()) {
            renderReports(new HashMap<>());
            return;
        }

        fetchEdgeStatuses(edgeIds, this::renderReports);
    }

    private void fetchEdgeStatuses(Set<String> edgeIds, Consumer<Map<String, String>> callback) {
        List<String> ids = new ArrayList<>(edgeIds);
        Map<String, String> statuses = new HashMap<>();
        List<List<String>> chunks = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += 10) {
            chunks.add(ids.subList(i, Math.min(i + 10, ids.size())));
        }

        int[] remaining = {chunks.size()};

        for (List<String> chunk : chunks) {
            FirebaseFirestore.getInstance()
                    .collection("edges")
                    .whereIn(FieldPath.documentId(), chunk)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null) {
                            for (QueryDocumentSnapshot doc : task.getResult()) {
                                statuses.put(doc.getId(), doc.getString("status"));
                            }
                        }
                        remaining[0]--;
                        if (remaining[0] == 0) {
                            callback.accept(statuses);
                        }
                    });
        }
    }

    private void showSignInPrompt() {
        recyclerView.setVisibility(View.GONE);
        emptyText.setVisibility(View.GONE);
        errorText.setVisibility(View.GONE);
        signInPromptText.setVisibility(View.VISIBLE);
    }

    private void showErrorState() {
        recyclerView.setVisibility(View.GONE);
        emptyText.setVisibility(View.GONE);
        signInPromptText.setVisibility(View.GONE);
        errorText.setVisibility(View.VISIBLE);
    }

    private void renderReports(Map<String, String> edgeStatuses) {
        signInPromptText.setVisibility(View.GONE);
        errorText.setVisibility(View.GONE);

        edgeStatusesMap.clear();
        if (edgeStatuses != null) {
            edgeStatusesMap.putAll(edgeStatuses);
        }

        boolean isEmpty = reportsList.isEmpty();
        emptyText.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);

        adapter = new ReportsAdapter(reportsList, edgeStatusesMap);
        recyclerView.setAdapter(adapter);
    }

    private void confirmDeleteReport(ReportEntry report, int position) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_delete_report_title)
                .setMessage(R.string.dialog_delete_report_message)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_delete_confirm, (dialog, which) -> deleteReportFromFirestore(report, position))
                .show();
    }

    private void deleteReportFromFirestore(ReportEntry report, int position) {
        if (report.getReportId() == null) return;

        FirebaseFirestore.getInstance()
                .collection("reports")
                .document(report.getReportId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    if (position >= 0 && position < reportsList.size()) {
                        reportsList.remove(position);
                        if (adapter != null) {
                            adapter.notifyItemRemoved(position);
                            adapter.notifyItemRangeChanged(position, reportsList.size());
                        }
                        if (reportsList.isEmpty()) {
                            recyclerView.setVisibility(View.GONE);
                            emptyText.setVisibility(View.VISIBLE);
                        }
                    }
                    Toast.makeText(MyReportsActivity.this, R.string.report_deleted_success, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(MyReportsActivity.this, R.string.report_failed, Toast.LENGTH_SHORT).show());
    }

    private CharSequence relativeDate(ReportEntry report) {
        if (report.getTimestamp() == null) {
            return "";
        }
        return DateUtils.getRelativeTimeSpanString(
                report.getTimestamp().getTime(),
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS);
    }

    private String issueTypeLabel(String issueType) {
        if (issueType == null) {
            return getString(R.string.other);
        }
        switch (issueType) {
            case "broken_lift":
                return getString(R.string.broken_lift);
            case "blocked_or_broken_ramp":
                return getString(R.string.blocked_or_broken_ramp);
            case "path_obstructed":
                return getString(R.string.path_obstructed);
            default:
                return getString(R.string.other);
        }
    }

    private class ReportsAdapter extends RecyclerView.Adapter<ReportsAdapter.ViewHolder> {

        private final List<ReportEntry> reports;
        private final Map<String, String> edgeStatuses;

        ReportsAdapter(List<ReportEntry> reports, Map<String, String> edgeStatuses) {
            this.reports = reports;
            this.edgeStatuses = edgeStatuses;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_report, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ReportEntry report = reports.get(position);

            holder.issueText.setText(issueTypeLabel(report.getIssueType()));
            holder.dateText.setText(relativeDate(report));

            boolean flagged = "flagged".equalsIgnoreCase(edgeStatuses.get(report.getEdgeId()));
            if (flagged) {
                holder.statusButton.setImageResource(R.drawable.ic_check);
                holder.statusButton.setImageTintList(ColorStateList.valueOf(getColor(R.color.colorAccent)));
                holder.statusButton.setContentDescription(getString(R.string.report_status_flagged));
            } else {
                holder.statusButton.setImageResource(R.drawable.ic_clock_outline);
                holder.statusButton.setImageTintList(ColorStateList.valueOf(getColor(R.color.colorRoute)));
                holder.statusButton.setContentDescription(getString(R.string.report_status_pending));
            }

            holder.deleteButton.setOnClickListener(v -> confirmDeleteReport(report, holder.getAdapterPosition()));
        }

        @Override
        public int getItemCount() {
            return reports.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView issueText;
            TextView dateText;
            ImageButton deleteButton;
            ImageButton statusButton;

            ViewHolder(View itemView) {
                super(itemView);
                issueText = itemView.findViewById(R.id.reportIssueTypeText);
                dateText = itemView.findViewById(R.id.reportDateText);
                deleteButton = itemView.findViewById(R.id.reportDeleteButton);
                statusButton = itemView.findViewById(R.id.reportStatusButton);
            }
        }
    }
}
