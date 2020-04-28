//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.aesh:aesh:2.6
//DEPS org.jsoup:jsoup:1.10.2

import org.aesh.AeshRuntimeRunner;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Option;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ExtractFolded {

    public static void main(String... args) throws Exception {
        AeshRuntimeRunner.builder().interactive(true).command(ExtractFolded.Cmd.class).args(args).execute();
    }


    private static final Pattern START_PATTERN = Pattern.compile("<g\\s*>");
    private static final Pattern END_PATTERN = Pattern.compile("</g\\s*>");
    private static final Pattern RECT_PATTERN = Pattern.compile(".*<rect.*");
    private static final Pattern FRAME_PATTERN = Pattern.compile("(.*)\\((([0-9]*\\,*)*[0-9]*) samples, ([0-9]*.[0-9]*)\\%\\)");


    @CommandDefinition(name = "LambdaNormalize", description = "Flamegraph Lambda name normalizer")
    public static class Cmd implements Command {

        private static final String DELIMITER = ";";
        @Option(required = true, shortName = 'p', description = "Path to flamegraph.svg")
        private static String path;

        @Override
        public CommandResult execute(CommandInvocation commandInvocation) throws CommandException, InterruptedException {

            File flamegraphFile = new File(path);

            if (!flamegraphFile.exists()) {
                System.err.println("File not found: ".concat(path));
                return CommandResult.FAILURE;
            }
            String extension = Optional.ofNullable(flamegraphFile.getAbsolutePath())
                    .filter(f -> f.contains("."))
                    .map(f -> f.substring(flamegraphFile.getAbsolutePath().lastIndexOf(".") + 1)).get();

            if (!extension.equals("svg")) {
                System.err.println("Not an .svg file: ".concat(path));
                return CommandResult.FAILURE;
            }

            try {
                LineConsumer consumer = new LineConsumer();
                Files.lines(Paths.get(path)).forEach(consumer);

                List<StackFrame> rootFrames = orderFrames(consumer.getFramesList());
                rootFrames.forEach(rootFrame -> {
                    StringBuilder outputBuilder = new StringBuilder();
                    writeCallStack(rootFrame, outputBuilder);
                });
            } catch (IOException e) {
                e.printStackTrace();
                return CommandResult.FAILURE;
            }

            return CommandResult.SUCCESS;
        }

        private List<StackFrame> orderFrames(List<StackFrame> frameList) {

            Collections.sort(frameList);

            AtomicReference<BigDecimal> rootFramesY = new AtomicReference<>(new BigDecimal(0));

            List<StackFrame> rootFrames = new LinkedList<>();

            frameList.forEach(extractedFrame -> {

                if (rootFrames.size() == 0) {
                    rootFramesY.set(extractedFrame.getY());
                    rootFrames.add(extractedFrame);
                } else if ((extractedFrame.getY() == rootFramesY.get() && !rootFrames.contains(extractedFrame))) {
                    rootFrames.add(extractedFrame);
                } else {
                    rootFrames.forEach(rootFrame -> {
                        if (LineConsumer.isSuperFrame(rootFrame, extractedFrame)) {
                            LineConsumer.insertFrame(rootFrame, extractedFrame);
                        }
                    });

                }
            });

            return rootFrames;
        }

        private void writeCallStack(StackFrame rootFrame, StringBuilder outputBuilder) {
            if (rootFrame.hasChildren()) {
                outputBuilder.append(rootFrame.getTitle()).append(DELIMITER);
                int pos = outputBuilder.length();
                AtomicInteger childSampleCount = new AtomicInteger();
                rootFrame.getChildStream().forEach(childFrame -> {
                    childSampleCount.addAndGet(childFrame.getSamples());
                    writeCallStack(childFrame, outputBuilder);
                    outputBuilder.setLength(pos);
                });
                if(childSampleCount.get() <  rootFrame.getSamples()){
                    outputBuilder.setLength(pos);

                    outputBuilder.append(" ").append(rootFrame.getSamples() - childSampleCount.get());
                    System.out.println(outputBuilder.toString());
                }
            } else {
                outputBuilder.append(rootFrame.getTitle()).append(" ").append(rootFrame.getSamples());
                System.out.println(outputBuilder.toString());
            }
        }
    }

    private static class LineConsumer implements Consumer<String> {

        private boolean found = false;

        private List<StackFrame> frames = new LinkedList<>();

        public List<StackFrame> getFramesList() {
            return frames;
        }

        @Override
        public void accept(String line) {

            if (START_PATTERN.matcher(line).matches()) {
                if (!found) {
                    found = true;
                } else {
                    System.err.println("Potential error: Found new <g> while already parsing");
                }
            } else if (END_PATTERN.matcher(line).matches()) {
                if (found) {
                    found = false;
                } else {
                    System.err.println("Potential error: Found new </g> without corresponding <g>");
                }
            } else if (RECT_PATTERN.matcher(line).matches()) {
                if (found) {
                    frames.add(extractStackFrame(line));
                } else {
                    System.err.println("Potential error: Found new <rect without corresponding <g>");
                }
            }


        }

        private static void insertFrame(StackFrame rootFrame, StackFrame extractedFrame) {
            if (ischild(rootFrame, extractedFrame)) {
                //this direct child
                rootFrame.addChild(extractedFrame);
            } else {
                //find next root frame
                Optional<StackFrame> nextFrameRoot = rootFrame.getChildStream().filter( childFrame -> isSuperFrame(childFrame, extractedFrame)).findFirst();
                if(nextFrameRoot.isPresent()){
                    insertFrame(nextFrameRoot.get(), extractedFrame);
                } else {
                    System.err.println("Frame was not inserted into tree: ".concat(extractedFrame.toString()));
                }
            }
        }

        private static boolean ischild(StackFrame rootFrame, StackFrame extractedFrame) {
            return isSuperFrame(rootFrame, extractedFrame) &&
                    (extractedFrame.getY().add(extractedFrame.getHeight()).add(new BigDecimal(1)).compareTo(rootFrame.getY()) == 0);
        }

        private static boolean isSuperFrame(StackFrame rootFrame, StackFrame extractedFrame) {
            return extractedFrame.getX().compareTo(rootFrame.getX()) >= 0
                    && extractedFrame.getX().add(extractedFrame.getWidth()).compareTo(rootFrame.getX().add(rootFrame.getWidth())) <= 0;

        }

        private static StackFrame extractStackFrame(String line) {
            Document snippet = Jsoup.parse(line);
            String x = snippet.select("rect[x]").first().attr("x");
            String y = snippet.select("rect[y]").first().attr("y");
            String width = snippet.select("rect[width]").first().attr("width");
            String height = snippet.select("rect[height]").first().attr("height");

            String title = snippet.head().tagName("title").select("title > title").first().text();

            Matcher titleMatcher = FRAME_PATTERN.matcher(title);

            if (titleMatcher.matches()) {
                String frame = titleMatcher.group(1);
                String samples = titleMatcher.group(2);
                String cpu = titleMatcher.group(4);

                assert (!frame.equals(""));
                assert (!samples.equals(""));
                assert (!cpu.equals(""));

                StackFrame stackFrame = new StackFrame(frame.trim()
                        , new BigDecimal(x)
                        , new BigDecimal(y)
                        , new BigDecimal(width)
                        , new BigDecimal(height)
                        , new BigDecimal(cpu)
                        , Integer.parseInt(samples.replaceAll(",", ""))
                );
                return stackFrame;

            } else {
                System.err.println("Could not parse stack frame: ".concat(line));
                return null;
            }

        }
    }

    private static class StackFrame implements Comparable<StackFrame> {

        private String title;
        private BigDecimal x, y, width, height, cpuPercent;
        private int samples;

        Set<StackFrame> childFrames = new LinkedHashSet<>();

        public StackFrame(String title, BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height, BigDecimal cpuPercent, int samples) {
            this.title = title;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.cpuPercent = cpuPercent;
            this.samples = samples;
        }

        public String getTitle() {
            return title;
        }

        public BigDecimal getX() {
            return x;
        }

        public BigDecimal getY() {
            return y;
        }

        public BigDecimal getWidth() {
            return width;
        }

        public BigDecimal getHeight() {
            return height;
        }

        public BigDecimal getCpuPercent() {
            return cpuPercent;
        }

        public int getSamples() {
            return samples;
        }

        public boolean hasChildren() {
            return this.childFrames.size() != 0;
        }

        public void addChild(StackFrame childFrame) {
            this.childFrames.add(childFrame);
        }

        public Stream<StackFrame> getChildStream(){
            return this.childFrames.stream();
        }


        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StackFrame that = (StackFrame) o;
            return samples == that.samples &&
                    Objects.equals(title, that.title) &&
                    Objects.equals(x, that.x) &&
                    Objects.equals(y, that.y) &&
                    Objects.equals(width, that.width) &&
                    Objects.equals(height, that.height) &&
                    Objects.equals(cpuPercent, that.cpuPercent) &&
                    Objects.equals(childFrames, that.childFrames);
        }

        @Override
        public int hashCode() {
            return Objects.hash(title, x, y, width, height, cpuPercent, samples, childFrames);
        }

        @Override
        public String toString() {
            return "StackFrame{" +
                    "title='" + title + '\'' +
                    ", x=" + x +
                    ", y=" + y +
                    ", width=" + width +
                    ", height=" + height +
                    ", cpuPercent=" + cpuPercent +
                    ", samples=" + samples +
                    ", childFrames=" + childFrames +
                    '}';
        }

        @Override
        public int compareTo(StackFrame stackFrame) {
            return stackFrame.getY().compareTo(this.getY());
        }
    }

}
