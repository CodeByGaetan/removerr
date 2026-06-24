package com.removerr.setting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_setting")
public class AppSetting {

    @Id
    @Column(name = "key", length = 100)
    private String key;

    @Column(name = "value", columnDefinition = "TEXT")
    private String value;

    @Column(name = "updated_at", length = 30, nullable = false)
    private String updatedAt;

    protected AppSetting() {}

    public AppSetting(String key, String value, String updatedAt) {
        this.key = key;
        this.value = value;
        this.updatedAt = updatedAt;
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
    public String getUpdatedAt() { return updatedAt; }

    public void setValue(String value) { this.value = value; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
