package com.github.jengelman.gradle.plugins.shadow.internal

import groovy.transform.CompileStatic

import java.nio.charset.Charset

@CompileStatic
class CleanProperties extends Properties {

    @Override
    Set<Map.Entry<Object, Object>> entrySet() {
        Set<Map.Entry<Object, Object>> sorted = new TreeSet<>(new Comparator<Map.Entry<Object, Object>>() {
            @Override
            int compare(Map.Entry<Object, Object> o1, Map.Entry<Object, Object> o2) {
                return String.valueOf(o1.key).compareTo(String.valueOf(o2.key))
            }
        })
        sorted.addAll(super.entrySet())
        return sorted
    }

    @Override
    Enumeration<Object> keys() {
        Vector<Object> sortedKeys = new Vector<>(super.keySet())
        Collections.sort(sortedKeys, new Comparator<Object>() {
            @Override
            int compare(Object o1, Object o2) {
                return String.valueOf(o1).compareTo(String.valueOf(o2))
            }
        })
        return sortedKeys.elements()
    }

    void writeWithoutComments(Charset charset, OutputStream os) {
        StringWriter writer = new StringWriter()
        super.store(writer, null)
        BufferedReader reader = new BufferedReader(new StringReader(writer.toString()))
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os, charset))
        String line
        while ((line = reader.readLine()) != null) {
            if (!line.startsWith("#")) {
                bw.write(line)
                bw.newLine()
            }
        }
        bw.flush()
    }
}
