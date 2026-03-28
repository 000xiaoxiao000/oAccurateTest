package com.oAT.web.esDao.entity;

import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.util.Objects;

public class LabelGroup implements Serializable {
    @Field(type = FieldType.Object)
    private Label labels[];
    @Field(type = FieldType.Keyword)
    private String groupName;

    /**
     * usecase, snapshot
     */
    @Field(type = FieldType.Keyword)
    private String type;
    @Field(type = FieldType.Keyword)
    private String projectid;

    public Label[] getLabels() {
        return labels;
    }

    public void setLabels(Label[] labels) {
        this.labels = labels;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getProjectid() {
        return projectid;
    }

    public void setProjectid(String projectid) {
        this.projectid = projectid;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public static class Label implements Serializable {
        @Field(type = FieldType.Keyword)
        private String name;
        @Field(type = FieldType.Keyword)
        private String color;

        public Label() {
        }

        public Label(String name, String color) {
            this.name = name;
            this.color = color;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getColor() {
            return color;
        }

        public void setColor(String color) {
            this.color = color;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Label label = (Label) o;
            return Objects.equals(name, label.name) && Objects.equals(color, label.color);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, color);
        }
    }

}
