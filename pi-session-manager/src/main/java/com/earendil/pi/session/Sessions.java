package com.earendil.pi.session;

import com.earendil.pi.common.Asyncs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 会话领域类型：树状会话（{@link Tree}）、持久化 SPI（{@link Repository}）与去重管理器（{@link Manager}）。 */
public final class Sessions {
    private Sessions() {}

    public enum Role { USER, ASSISTANT, TOOL_RESULT, SYSTEM }

    public static final class ToolCallSnapshot {
        private final String id;
        private final String name;
        private final Map<String,Object> arguments;
        public ToolCallSnapshot(String id, String name, Map<String,Object> arguments) {
            this.id=Asyncs.nonBlank(id,"id");
            this.name=Asyncs.nonBlank(name,"name");
            this.arguments=Collections.unmodifiableMap(arguments==null
                    ? new LinkedHashMap<String,Object>()
                    : new LinkedHashMap<String,Object>(arguments));
        }
        public String getId(){return id;}
        public String getName(){return name;}
        public Map<String,Object> getArguments(){return arguments;}
    }

    /** 会话树中的单个节点：user/assistant/tool_result/system 之一的不可变消息。 */
    public static final class Node {
        private final String id;
        private final String parentId;
        private final Role role;
        private final String content;
        private final List<ToolCallSnapshot> toolCalls;
        private final String toolCallId;
        private final String toolName;
        private final boolean error;

        private Node(String parentId, Role role, String content, List<ToolCallSnapshot> calls,
                     String toolCallId, String toolName, boolean error) {
            this.id=UUID.randomUUID().toString();
            this.parentId=parentId;
            this.role=Asyncs.require(role,"role");
            this.content=content==null?"":content;
            this.toolCalls=Collections.unmodifiableList(calls==null
                    ? new ArrayList<ToolCallSnapshot>()
                    : new ArrayList<ToolCallSnapshot>(calls));
            this.toolCallId=toolCallId;
            this.toolName=toolName;
            this.error=error;
        }
        public static Node user(String parent,String content){return new Node(parent,Role.USER,content,null,null,null,false);}
        public static Node system(String parent,String content){return new Node(parent,Role.SYSTEM,content,null,null,null,false);}
        public static Node assistant(String parent,String content,List<ToolCallSnapshot> calls){
            return new Node(parent,Role.ASSISTANT,content,calls,null,null,false);
        }
        public static Node tool(String parent,String callId,String toolName,String content,boolean error){
            return new Node(parent,Role.TOOL_RESULT,content,null,
                    Asyncs.nonBlank(callId,"callId"),Asyncs.nonBlank(toolName,"toolName"),error);
        }
        public String getId(){return id;}
        public String getParentId(){return parentId;}
        public Role getRole(){return role;}
        public String getContent(){return content;}
        public List<ToolCallSnapshot> getToolCalls(){return toolCalls;}
        public String getToolCallId(){return toolCallId;}
        public String getToolName(){return toolName;}
        public boolean isError(){return error;}
    }

    /** 树状会话：保留全部节点，{@link #activePath()} 返回唯一会发给模型的活跃路径。 */
    public static final class Tree {
        private final String id;
        private final Map<String,Node> nodes=new LinkedHashMap<String,Node>();
        private String activeTipId;
        public Tree(String id){this.id=Asyncs.nonBlank(id,"id");}
        public synchronized Node appendUser(String content){return append(Node.user(activeTipId,content));}
        public synchronized Node appendAssistant(String content,List<ToolCallSnapshot> calls){
            return append(Node.assistant(activeTipId,content,calls));
        }
        public synchronized Node appendTool(String callId,String toolName,String content,boolean error){
            return append(Node.tool(activeTipId,callId,toolName,content,error));
        }
        public synchronized Node branchFrom(String fromId,String content){
            requireNode(fromId); return append(Node.user(fromId,content));
        }
        public synchronized void rewindTo(String id){requireNode(id); activeTipId=id;}
        public synchronized List<Node> activePath(){
            List<Node> reverse=new ArrayList<Node>();
            String cursor=activeTipId;
            while(cursor!=null){
                Node node=nodes.get(cursor);
                if(node==null) throw new IllegalStateException("broken session tree at "+cursor);
                reverse.add(node); cursor=node.getParentId();
            }
            Collections.reverse(reverse);
            return Collections.unmodifiableList(reverse);
        }
        public synchronized List<Node> allNodes(){return Collections.unmodifiableList(new ArrayList<Node>(nodes.values()));}
        private Node append(Node node){nodes.put(node.getId(),node);activeTipId=node.getId();return node;}
        private void requireNode(String id){if(!nodes.containsKey(id))throw new IllegalArgumentException("node not found: "+id);}
        public String getId(){return id;}
    }

    /** 会话持久化 SPI；实现方可内存、JSONL 或数据库。 */
    public interface Repository {
        CompletableFuture<Optional<Tree>> find(String id);
        CompletableFuture<Void> save(Tree tree);
    }

    public static final class InMemoryRepository implements Repository {
        private final ConcurrentMap<String,Tree> data=new ConcurrentHashMap<String,Tree>();
        public CompletableFuture<Optional<Tree>> find(String id){
            return CompletableFuture.completedFuture(Optional.ofNullable(data.get(Asyncs.nonBlank(id,"id"))));
        }
        public CompletableFuture<Void> save(Tree tree){
            data.put(Asyncs.require(tree,"tree").getId(),tree);
            return CompletableFuture.completedFuture(null);
        }
    }

    /** 会话访问入口：getOrCreate 以 per-id single-flight 包住整个 find-or-create，并发调用共享同一创建过程，结束后清理。 */
    public static final class Manager {
        private final Repository repository;
        private final ConcurrentMap<String,CompletableFuture<Tree>> creations=new ConcurrentHashMap<String,CompletableFuture<Tree>>();
        public Manager(Repository repository){this.repository=Asyncs.require(repository,"repository");}
        public CompletableFuture<Tree> getOrCreate(final String id){
            Asyncs.nonBlank(id,"id");
            final CompletableFuture<Tree> flight=new CompletableFuture<Tree>();
            final CompletableFuture<Tree> existing=creations.putIfAbsent(id,flight);
            if(existing!=null)return existing;
            final CompletableFuture<Tree> work;
            try{
                work=repository.find(id).thenCompose(found -> {
                    if(found.isPresent())return CompletableFuture.completedFuture(found.get());
                    final Tree created=new Tree(id);
                    return repository.save(created).thenApply(ignored -> created);
                });
            }catch(Throwable error){
                flight.completeExceptionally(error);
                creations.remove(id,flight);
                return flight;
            }
            work.whenComplete((tree,error)->{
                try{
                    if(error!=null)flight.completeExceptionally(Asyncs.unwrap(error));
                    else flight.complete(tree);
                }finally{
                    creations.remove(id,flight);
                }
            });
            return flight;
        }
        public CompletableFuture<Optional<Tree>> find(String id){return repository.find(Asyncs.nonBlank(id,"id"));}
        public CompletableFuture<Void> save(Tree tree){return repository.save(tree);}
    }
}
