package ua.kpi.project.compatme.adapter.out.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for the optional Atlas/Atlas Local vector-search shortlist. */
@ConfigurationProperties(prefix = "app.vector-search")
public class AtlasVectorSearchProperties {

    private boolean enabled;
    private String selfIndex = "profile-self-embeddings";
    private String preferenceIndex = "profile-preference-embeddings";
    private int candidatesPerResult = 20;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getSelfIndex() { return selfIndex; }
    public void setSelfIndex(String selfIndex) { this.selfIndex = selfIndex; }
    public String getPreferenceIndex() { return preferenceIndex; }
    public void setPreferenceIndex(String preferenceIndex) { this.preferenceIndex = preferenceIndex; }
    public int getCandidatesPerResult() { return candidatesPerResult; }
    public void setCandidatesPerResult(int candidatesPerResult) { this.candidatesPerResult = candidatesPerResult; }
}
