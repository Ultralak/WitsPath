package com.example.witspath.ui;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.witspath.ui.BaseActivity;

import com.example.witspath.R;
import com.example.witspath.model.ReportEntry;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldPath;
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

/**
 * Team Wavelets - WitsPath
 * Shows every report the signed-in user has submitted (reports/{reportId} where
 * userId == current uid), newest first. Cross-references each report's edgeId
 * against edges/{edgeId}.status so the user can see whether their report is
 * still pending or has tipped the edge into "flagged" (3 unique reports, per
 * the schema note in wavelets-graph.json / EdgeUpdateListener).
 * Guests (no Firebase user, or an anonymous one) see a sign-in prompt instead
 * of a list, since anonymous reports aren't tied to a stable identity that can
 * be re-queried across sessions.
 */
public class MyReportsActivity extends BaseActivity {

    private LinearLayout container;
    private View emptyText;
    private View signInPromptText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_reports);

        MaterialToolbar toolbar = findViewById(R.id.myReportsToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        container = findViewById(R.id.myReportsContainer);
        emptyText = findViewById(R.id.myReportsEmptyText);
        signInPromptText = findViewById(R.id.myReportsSignInPromptText);
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
                .addOnFailureListener(e -> renderReports(new ArrayList<>(), new HashMap<>()));
    }

    private void onReportsLoaded(QuerySnapshot snapshot) {
        List<ReportEntry> reports = new ArrayList<>();
        Set<String> edgeIds = new HashSet<>();
        for (QueryDocumentSnapshot doc : snapshot) {
            ReportEntry entry = doc.toObject(ReportEntry.class);
            entry.setReportId(doc.getId());
            reports.add(entry);
            if (entry.getEdgeId() != null) {
                edgeIds.add(entry.getEdgeId());
            }
        }

        if (edgeIds.isEmpty()) {
            renderReports(reports, new HashMap<>());
            return;
        }

        fetchEdgeStatuses(edgeIds, statuses -> renderReports(reports, statuses));
    }

    /** Firestore whereIn caps at 10 values per query, so this chunks the edgeId set. */
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
        container.removeAllViews();
        container.setVisibility(View.GONE);
        emptyText.setVisibility(View.GONE);
        signInPromptText.setVisibility(View.VISIBLE);
    }

    private void renderReports(List<ReportEntry> reports, Map<String, String> edgeStatuses) {
        signInPromptText.setVisibility(View.GONE);
        container.removeAllViews();

        emptyText.setVisibility(reports.isEmpty() ? View.VISIBLE : View.GONE);
        container.setVisibility(reports.isEmpty() ? View.GONE : View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (ReportEntry report : reports) {
            View row = inflater.inflate(R.layout.item_report, container, false);

            TextView issueText = row.findViewById(R.id.reportIssueTypeText);
            TextView dateText = row.findViewById(R.id.reportDateText);
            TextView statusText = row.findViewById(R.id.reportStatusText);

            issueText.setText(issueTypeLabel(report.getIssueType()));
            dateText.setText(relativeDate(report));

            boolean flagged = "flagged".equalsIgnoreCase(edgeStatuses.get(report.getEdgeId()));
            statusText.setText(flagged ? R.string.report_status_flagged : R.string.report_status_pending);
            statusText.setTextColor(getColor(flagged ? R.color.colorFlag : R.color.colorRoute));

            container.addView(row);
        }
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
}
