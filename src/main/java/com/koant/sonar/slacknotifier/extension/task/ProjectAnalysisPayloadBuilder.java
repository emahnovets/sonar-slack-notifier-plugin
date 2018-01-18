package com.koant.sonar.slacknotifier.extension.task;

import com.github.seratch.jslack.api.model.Attachment;
import com.github.seratch.jslack.api.model.Field;
import com.github.seratch.jslack.api.webhook.Payload;
import com.koant.sonar.slacknotifier.common.component.ProjectConfig;
import org.sonar.api.ce.posttask.PostProjectAnalysisTask;
import org.sonar.api.ce.posttask.QualityGate;
import org.sonar.api.i18n.I18n;
import org.sonar.api.measures.CoreMetrics;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ak on 18/10/16.
 * Modified by poznachowski
 */

public class ProjectAnalysisPayloadBuilder {
    private static final String SLACK_GOOD_COLOUR = "good";
    private static final String SLACK_WARNING_COLOUR = "warning";
    private static final String SLACK_DANGER_COLOUR = "danger";
    private static final Map<QualityGate.EvaluationStatus, String> statusToColor = new EnumMap<>(QualityGate.EvaluationStatus.class);

    static {
        statusToColor.put(QualityGate.EvaluationStatus.OK, SLACK_GOOD_COLOUR);
        statusToColor.put(QualityGate.EvaluationStatus.WARN, SLACK_WARNING_COLOUR);
        statusToColor.put(QualityGate.EvaluationStatus.ERROR, SLACK_DANGER_COLOUR);
    }

    private static final List<String> countersConditionKeys = new ArrayList<>();

    static {
        countersConditionKeys.add(CoreMetrics.CRITICAL_VIOLATIONS_KEY);
        countersConditionKeys.add(CoreMetrics.BLOCKER_VIOLATIONS_KEY);
        countersConditionKeys.add(CoreMetrics.MAJOR_VIOLATIONS_KEY);
        countersConditionKeys.add(CoreMetrics.MINOR_VIOLATIONS_KEY);
    }

    I18n i18n;
    PostProjectAnalysisTask.ProjectAnalysis analysis;
    private ProjectConfig projectConfig;
    private String slackUser;
    private String projectUrl;

    private ProjectAnalysisPayloadBuilder(PostProjectAnalysisTask.ProjectAnalysis analysis) {
        this.analysis = analysis;
    }

    public static ProjectAnalysisPayloadBuilder of(PostProjectAnalysisTask.ProjectAnalysis analysis) {
        return new ProjectAnalysisPayloadBuilder(analysis);
    }

    public ProjectAnalysisPayloadBuilder projectConfig(ProjectConfig projectConfig) {
        this.projectConfig = projectConfig;
        return this;
    }

    public ProjectAnalysisPayloadBuilder i18n(I18n i18n) {
        this.i18n = i18n;
        return this;
    }

    public ProjectAnalysisPayloadBuilder username(String slackUser) {
        this.slackUser = slackUser;
        return this;
    }

    public ProjectAnalysisPayloadBuilder projectUrl(String projectUrl) {
        this.projectUrl = projectUrl;
        return this;
    }

    public Payload build() {
        assertNotNull(projectConfig, "projectConfig");
        assertNotNull(projectUrl, "projectUrl");
        assertNotNull(slackUser, "slackUser");
        assertNotNull(i18n, "i18n");
        assertNotNull(analysis, "analysis");

        QualityGate qualityGate = analysis.getQualityGate();
        String shortText = String.join("",
                "Project [", analysis.getProject().getName(), "] analyzed",
                qualityGate == null ? "." : ". Quality gate status: " + getQualityGateEmoji(qualityGate));

        return Payload.builder()
                .channel(projectConfig.getSlackChannel())
                .username(slackUser)
                .text(shortText)
                .attachments(qualityGate == null ? null : buildConditionsAttachment(qualityGate, projectConfig.isQgFailOnly()))
                .build();
    }

    private String getQualityGateEmoji(QualityGate qualityGate) {
        if (qualityGate.getStatus().equals(QualityGate.Status.OK)) {
            return "Ok :party_parrot:";
        } else if (qualityGate.getStatus().equals(QualityGate.Status.ERROR)) {
            return "Error :facepalm_skype:";
        }

        return "Warning :alert:";
    }

    private void assertNotNull(Object object, String argumentName) {
        if (object == null) {
            throw new IllegalArgumentException("[Assertion failed] - " + argumentName + " argument is required; it must not be null");
        }
    }

    private List<Attachment> buildConditionsAttachment(QualityGate qualityGate, boolean qgFailOnly) {
        List<Attachment> attachments = new ArrayList<>();

        attachments.add(getCountersCondition(qualityGate.getConditions()));
        attachments.addAll(qualityGate.getConditions()
            .stream()
            .filter(condition -> !countersConditionKeys.contains(condition.getMetricKey()))
            .map(this::getAttachment)
            .collect(Collectors.toList()));

        return attachments;
    }

    private Attachment getCountersCondition(Collection<QualityGate.Condition> conditions) {
        return Attachment.builder()
            .fields(
                conditions
                    .stream()
                    .filter(condition -> countersConditionKeys.contains(condition.getMetricKey()))
                    .map(condition -> Field
                        .builder()
                        .title(getConditionName(condition))
                        .value(condition.getValue())
                        .valueShortEnough(true)
                        .build()
                    )
                    .collect(Collectors.toList())
            )
            .color("#2d9ee0")
            .build();
    }

    private Attachment getAttachment(QualityGate.Condition condition) {
        return Attachment.builder()
            .title(getConditionName(condition))
            .text(condition.getValue())
            .color(statusToColor.get(condition.getStatus()))
            .build();
    }

    private String getConditionName(QualityGate.Condition condition) {
        String conditionMetricKey = condition.getMetricKey();
        String i18nKey = "metric." + conditionMetricKey + ".name";

        return i18n.message(Locale.ENGLISH, i18nKey, conditionMetricKey);
    }
}
