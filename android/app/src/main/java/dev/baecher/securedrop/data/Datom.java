package dev.baecher.securedrop.data;

import org.json.JSONObject;

import java.io.PrintWriter;

public class Datom {
    final public int entity;
    final public String attribute;
    final public String value;
    final public Integer refValue;

    public Datom(int entity, String attribute, String value) {
        this.entity = entity;
        this.attribute = attribute;
        this.value = value;
        this.refValue = null;
    }

    public Datom(int entity, String attribute, int refValue) {
        this.entity = entity;
        this.attribute = attribute;
        this.value = null;
        this.refValue = refValue;
    }

    public void write(PrintWriter pw) {
        pw.print('[');
        pw.print(this.entity);
        pw.print(',');
        pw.print(JSONObject.quote(this.attribute));
        pw.print(',');
        if (this.value != null) {
            pw.print(JSONObject.quote(this.value));
        } else {
            pw.print(this.refValue);
        }
        pw.print(']');
    }
}
