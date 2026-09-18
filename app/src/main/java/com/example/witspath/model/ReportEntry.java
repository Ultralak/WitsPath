package com.example.witspath.model;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

/**
 * Team Wavelets - WitsPath
 * Maps a reports/{reportId} Firestore document (userId, edgeId, issueType, timestamp)
 * for use in MyReportsActivity. reportId isn't itself a field in the document —
 * set it from the DocumentSnapshot's id after deserializing.
 */
public class ReportEntry {

    private String reportId;
    private String userId;
    private String edgeId;
    private String issueType;
    @ServerTimestamp
    private Date timestamp;

    public ReportEntry() {
    }

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEdgeId() {
        return edgeId;
    }

    public void setEdgeId(String edgeId) {
        this.edgeId = edgeId;
    }

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }
}