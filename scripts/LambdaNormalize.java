//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.aesh:aesh:2.6

import org.aesh.AeshRuntimeRunner;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LambdaNormalize {

    private static Pattern pattern = Pattern.compile(".*(\\$\\$Lambda\\$[0-9]*/[0-9]*)\\..*");

    public static void main(String... args) throws Exception {
        AeshRuntimeRunner.builder().interactive(true).command(ProcessFoldedCmd.class).args(args).execute();
    }

    @CommandDefinition(name = "LambdaNormalize", description = "Flamegraph Lambda name normalizer")
    public static class ProcessFoldedCmd implements Command {

        @Option(required = true, shortName = 'p', description = "Path to folded stacks")
        private static String path;

        @Override
        public CommandResult execute(CommandInvocation commandInvocation) throws CommandException, InterruptedException {
            final String FRAME_DELIMITER = ";";
            final String LAMBDA_FRAME_PREFIX = "Lambda_";

            List<LambdaFrame> foundFrames = new ArrayList<>();
            Map<LambdaFrame, String> newLambdaNames = new ConcurrentHashMap<>();
            AtomicInteger counter = new AtomicInteger();

            try {
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
            } catch (NoSuchFileException nfe){
                System.out.println("File does not exist: ".concat(nfe.getLocalizedMessage()));
                return CommandResult.FAILURE;
            }
            catch (IOException e) {
                e.printStackTrace();
                return CommandResult.FAILURE;
            }

            return CommandResult.SUCCESS;
        }
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
