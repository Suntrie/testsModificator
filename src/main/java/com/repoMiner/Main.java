package com.repoMiner;

import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws XmlPullParserException, IOException, ClassNotFoundException {
        new TestsModificator("/home/suntrie/IdeaProjects/testable", "/home/suntrie/IdeaProjects/testable/src/test/java");
    }
}
