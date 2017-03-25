package scala.tools.nsc;

import java.nio.file.SimpleFileVisitor;

// workaround for https://github.com/scala/scala-dev/issues/345
public class SimpleFileVisitor1<T> extends SimpleFileVisitor<T> {
}
