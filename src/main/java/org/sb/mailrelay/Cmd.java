package org.sb.mailrelay;

import java.io.IOException;
import java.nio.file.Path;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface Cmd
{
    default Try<Void, IOException> exec2(Path home, List<String> opts)
    {
        try
        {
            exec(home, opts);
            return Try.success((Void)null);
        }
        catch(IOException e)
        {
            return Try.failure(e);
        }
    }
    
    void exec(Path home, List<String> opts) throws IOException, IllegalStateException;

    static Set<String> booleanFlags(final Stream<String> flags)
    {
        return flags.filter(flag -> !flag.contains("=")).map(flag -> flag.toLowerCase()).collect(Collectors.toSet());
    }
    
    static Map<String, String> nvpFlags(final Stream<String> flags)
    {
        return flags.map(flag -> new SimpleImmutableEntry<>(flag, flag.indexOf("=")))
                .filter(e -> e.getValue() > 0)
                .collect(Collectors.toMap(e -> e.getKey().substring(0, e.getValue()).toLowerCase(), 
                                           e -> dequote(e.getKey().substring(e.getValue() + 1))));
    }
    
    static String dequote(String str)
    {
        return (str.startsWith("\"") && str.endsWith("\""))
                || (str.startsWith("\'") && str.endsWith("\'")) ? str.substring(1, str.length() -1) : str;
    }
    
    default List<String> help(String name)
    {
        return Collections.singletonList(name + "\t not implemented");
    }
}
