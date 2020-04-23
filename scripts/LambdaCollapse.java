//usr/bin/env jbang "$0" "$@" ; exit $?
package com.github.stuartwdouglas.speedtest;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LambdaCollapse {

    private static Pattern pattern = Pattern.compile(".*(\\$\\$Lambda\\$[0-9]*/[0-9]*)\\..*");

    public static void main(String... args) throws Exception {
        String path = args[0];

        final String FRAME_DELIMITER = ";";
        final String LAMBDA_FRAME_PREFIX = "Lambda_";

        List<LambdaFrame> foundFrames = new ArrayList<>();
        Map<LambdaFrame, String> newLambdaNames = new ConcurrentHashMap<>();
        AtomicInteger counter = new AtomicInteger();

        Files.lines(Paths.get(path)).forEach(
                line -> {
                    String outputLine;
                    if (pattern.matcher(line).matches()) {
                        String[] frames = line.split(FRAME_DELIMITER);
                        for (int i = 0; i < frames.length - 1; i++) {
                            Matcher matcher = pattern.matcher(frames[i]);
                            if (matcher.matches()) {
                                LambdaFrame foundFrame = new LambdaFrame(matcher.group(1), (i != 0) ? frames[i - 1] : "", frames[i], (i + 1 <= frames.length) ? frames[i + 1] : "");
                                foundFrames.add(foundFrame);
                                if (!newLambdaNames.containsKey(foundFrame)) {
                                    newLambdaNames.put(foundFrame, LAMBDA_FRAME_PREFIX.concat(String.valueOf(counter.get())));
                                    counter.incrementAndGet();
                                }
                                frames[i] = foundFrame.lambdaNewName = newLambdaNames.get(foundFrame);
                            }

                        }
                        outputLine = String.join(FRAME_DELIMITER, frames);
                    } else {
                        outputLine = line;
                    }
                    System.out.println(outputLine);
                }
        );

    }


    private static class LambdaFrame {
        public String parentFrame;
        public String lambdaFrame;
        public String childFrame;
        public String lambdaOldName;
        public String lambdaNewName;


        public LambdaFrame(String oldName, String parentFrame, String lambdaFrame, String childFrame) {
            this.parentFrame = parentFrame;
            this.lambdaFrame = lambdaFrame;
            this.childFrame = childFrame;
            this.lambdaOldName = oldName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LambdaFrame that = (LambdaFrame) o;
            return Objects.equals(parentFrame, that.parentFrame) &&
                    Objects.equals(childFrame, that.childFrame);
        }

        @Override
        public int hashCode() {
            return Objects.hash(parentFrame, childFrame);
        }
    }
}
