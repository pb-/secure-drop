package dev.baecher.securedrop.data;

import java.io.PrintWriter;
import java.util.LinkedList;

import dev.baecher.securedrop.data.Datom;

public class Datoms extends LinkedList<Datom> {
    public void write(PrintWriter pw) {
        pw.write('[');
        for (int i = 0; i < size(); i++) {
            if (i > 0) {
                pw.write(',');
            }
            get(i).write(pw);
        }
        pw.write(']');
    }
}
