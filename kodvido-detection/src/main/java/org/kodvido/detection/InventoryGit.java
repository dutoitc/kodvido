// SPDX-License-Identifier: GPL-3.0-only
package org.kodvido.detection;
import org.kodvido.domain.Inventory.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
/** Local status and remote advertisement only. Never fetches, checks out or refreshes the index. */
public final class InventoryGit {
    record Result(int exit,String output) {}
    @FunctionalInterface interface Runner { Result run(List<String> args,boolean network) throws Exception; }
    public record Evidence(String workingTree,List<Remote> remotes) {}
    private final Runner runner;
    public InventoryGit() {this(InventoryGit::run);}
    InventoryGit(Runner runner) {this.runner=runner;}
    public Evidence inspect(Path root) {
        String now=Instant.now().toString();
        try {
            SafePaths.directory(root);
            if(!Files.exists(root.resolve(".git"),LinkOption.NOFOLLOW_LINKS)) return single("Sans Git",RemoteState.NO_GIT,now);
            Path metadata=SafePaths.directory(root.resolve(".git"));
            if(!new GitInspector(Clock.systemUTC()).safeMetadata(metadata)) return single("Non pris en charge",RemoteState.UNSUPPORTED,now);
            var local=new ArrayList<>(List.of("--git-dir="+metadata,"--work-tree="+root,"-c","core.fsmonitor=false","-c","core.untrackedCache=false"));
            var status=new ArrayList<>(local);status.addAll(List.of("status","--porcelain=v1","--untracked-files=normal","--ignore-submodules=all"));
            var state=runner.run(status,false);
            String work=state.exit()==0?(state.output().isBlank()?"Propre":"Modifications locales"):"Non vérifié";
            var config=runner.run(List.of("config","--no-includes","--file",metadata.resolve("config").toString(),"--get-regexp","^remote\\..*\\.url$"),false);
            var remotes=new ArrayList<Remote>();
            for(String line:config.output().lines().limit(10).toList()) {
                String[] parts=line.split("\\s+",2);
                if(parts.length!=2 || !parts[0].startsWith("remote.") || !parts[0].endsWith(".url")) continue;
                String name=parts[0].substring(7,parts[0].length()-4);
                String url=parts[1].strip();
                if(!allowed(url)) {remotes.add(new Remote(name,"[adresse non prise en charge ou contenant des identifiants]",RemoteState.UNSUPPORTED,now));continue;}
                var result=runner.run(List.of("ls-remote","--symref","--",url,"HEAD"),true);
                remotes.add(new Remote(name,url,classify(result),Instant.now().toString()));
            }
            if(remotes.isEmpty()) remotes.add(new Remote("","",RemoteState.NO_REMOTE,now));
            return new Evidence(work,List.copyOf(remotes));
        } catch(InterruptedException e) {Thread.currentThread().interrupt();return single("Interrompu",RemoteState.NOT_CHECKED,now);}
        catch(Exception e) {return single("Non vérifié",RemoteState.ERROR,now);}
    }
    private static Evidence single(String work,RemoteState state,String now) {return new Evidence(work,List.of(new Remote("","",state,now)));}
    static boolean allowed(String url) {
        if(url.length()>2048 || url.chars().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c)) || url.contains("?") || url.contains("#")) return false;
        if(url.matches("[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+:[a-zA-Z0-9_./~-]+")) return true;
        try {
            var uri=java.net.URI.create(url);
            return Set.of("http","https","ssh").contains(uri.getScheme()) && uri.getHost()!=null
                && (uri.getUserInfo()==null || (uri.getScheme().equals("ssh") && uri.getUserInfo().matches("[a-zA-Z0-9._-]+")));
        } catch(Exception e) {return false;}
    }
    static RemoteState classify(Result r) {
        if(r.exit()==0) return RemoteState.ACCESSIBLE; // Empty repositories advertise no HEAD but still exist.
        if(r.exit()==124) return RemoteState.TIMEOUT;
        String text=r.output().toLowerCase(Locale.ROOT);
        if(text.contains("404") || text.contains("not found") || text.contains("does not exist")) return RemoteState.NOT_FOUND_OR_PRIVATE;
        if(text.contains("401") || text.contains("403") || text.contains("authentication") || text.contains("permission denied") || text.contains("could not read username")) return RemoteState.ACCESS_DENIED;
        if(text.contains("timed out") || text.contains("timeout")) return RemoteState.TIMEOUT;
        if(text.contains("resolve host") || text.contains("resolve hostname") || text.contains("connect") || text.contains("network is unreachable")) return RemoteState.UNREACHABLE;
        return RemoteState.ERROR;
    }
    static Result run(List<String> args,boolean network) throws Exception {
        var command=new ArrayList<>(List.of("git","--no-pager","--no-replace-objects","--no-optional-locks","-c","gc.auto=0","-c","maintenance.auto=false"));
        if(network) command.addAll(List.of("-c","credential.interactive=false","-c","protocol.allow=never","-c","protocol.https.allow=always","-c","protocol.http.allow=always","-c","protocol.ssh.allow=always"));
        command.addAll(args);
        var builder=new ProcessBuilder(command).redirectErrorStream(true);
        var env=builder.environment();env.keySet().removeIf(k->k.toUpperCase(Locale.ROOT).startsWith("GIT_"));
        env.put("GIT_NO_LAZY_FETCH","1");env.put("LC_ALL","C");env.put("GIT_TERMINAL_PROMPT","0");env.put("GCM_INTERACTIVE","Never");env.put("GIT_ASKPASS","false");env.put("SSH_ASKPASS","false");
        env.put("GIT_SSH_COMMAND","ssh -o BatchMode=yes -o StrictHostKeyChecking=yes -o ConnectTimeout=5");
        if(!network) {env.put("GIT_CONFIG_NOSYSTEM","1");env.put("GIT_CONFIG_GLOBAL",System.getProperty("os.name").startsWith("Windows")?"NUL":"/dev/null");}
        // Isolate executable lookup and Git repository discovery from the launch/scanned directory.
        Path cwd=Files.createTempDirectory("kodvido-git-");builder.directory(cwd.toFile());
        env.put("GIT_CEILING_DIRECTORIES",cwd.getParent().toString());
        try {
        Process p=builder.start();p.getOutputStream().close();
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var output=executor.submit(()->{try(var in=p.getInputStream();var bytes=new java.io.ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1) {if(bytes.size()<16384) bytes.write(buffer,0,Math.min(n,16384-bytes.size()));}return bytes.toString(StandardCharsets.UTF_8);
            }});
            try {
                if(!p.waitFor(12,TimeUnit.SECONDS)) {p.descendants().forEach(ProcessHandle::destroyForcibly);p.destroyForcibly();return new Result(124,"");}
                return new Result(p.exitValue(),output.get(2,TimeUnit.SECONDS));
            } finally {p.descendants().forEach(ProcessHandle::destroyForcibly);p.destroyForcibly();p.getInputStream().close();output.cancel(true);}
        }
        } finally {Files.deleteIfExists(cwd);}
    }
}
