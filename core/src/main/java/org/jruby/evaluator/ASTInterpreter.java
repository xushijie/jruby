/*
 ******************************************************************************
 * BEGIN LICENSE BLOCK *** Version: EPL 1.0/GPL 2.0/LGPL 2.1
 * 
 * The contents of this file are subject to the Eclipse Public License Version
 * 1.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 * 
 * Copyright (C) 2006 Charles Oliver Nutter <headius@headius.com>
 * Copytight (C) 2006-2007 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2007 Miguel Covarrubias <mlcovarrubias@gmail.com>
 * Copyright (C) 2007 Ola Bini <ola@ologix.com>
 * 
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"), in
 * which case the provisions of the GPL or the LGPL are applicable instead of
 * those above. If you wish to allow use of your version of this file only under
 * the terms of either the GPL or the LGPL, and not to allow others to use your
 * version of this file under the terms of the EPL, indicate your decision by
 * deleting the provisions above and replace them with the notice and other
 * provisions required by the GPL or the LGPL. If you do not delete the
 * provisions above, a recipient may use your version of this file under the
 * terms of any one of the EPL, the GPL or the LGPL. END LICENSE BLOCK ****
 ******************************************************************************/

package org.jruby.evaluator;

import org.jruby.EvalType;
import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyLocalJumpError;
import org.jruby.RubyModule;
import org.jruby.RubyString;
import org.jruby.ast.ArrayNode;
import org.jruby.ast.BlockPassNode;
import org.jruby.ast.IterNode;
import org.jruby.ast.MultipleAsgnNode;
import org.jruby.ast.Node;
import org.jruby.ast.util.ArgsUtil;
import org.jruby.common.IRubyWarnings.ID;
import org.jruby.exceptions.JumpException;
import org.jruby.runtime.Helpers;
import org.jruby.parser.StaticScope;
import org.jruby.runtime.Block;
import org.jruby.runtime.DynamicScope;
import org.jruby.runtime.RubyEvent;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.Binding;
import org.jruby.runtime.Frame;
import org.jruby.RubyInstanceConfig.CompileMode;
import org.jruby.ir.interpreter.Interpreter;

public class ASTInterpreter {
    public static IRubyObject INTERPRET_METHOD(
            Ruby runtime,
            ThreadContext context,
            String file,
            int line,
            RubyModule implClass,
            Node node, String name,
            IRubyObject self,
            Block block,
            boolean isTraceable) {
        try {
            ThreadContext.pushBacktrace(context, name, file, line);
            if (isTraceable) methodPreTrace(runtime, context, name, implClass);
            return null;
        } finally {
            if (isTraceable) {
                try {methodPostTrace(runtime, context, name, implClass);}
                finally {ThreadContext.popBacktrace(context);}
            } else {
                ThreadContext.popBacktrace(context);
            }
        }
    }

    public static IRubyObject INTERPRET_BLOCK(Ruby runtime, ThreadContext context, String file, int line, Node node, String name, IRubyObject self, Block block) {
        try {
            ThreadContext.pushBacktrace(context, name, file, line);
            blockPreTrace(runtime, context, name, self.getType());
            return null;
        } finally {
            blockPostTrace(runtime, context, name, self.getType());
            ThreadContext.popBacktrace(context);
        }
    }

    private static void methodPreTrace(Ruby runtime, ThreadContext context, String name, RubyModule implClass) {
        if (runtime.hasEventHooks()) context.trace(RubyEvent.CALL, name, implClass);
    }

    private static void methodPostTrace(Ruby runtime, ThreadContext context, String name, RubyModule implClass) {
        if (runtime.hasEventHooks()) context.trace(RubyEvent.RETURN, name, implClass);
    }

    private static void blockPreTrace(Ruby runtime, ThreadContext context, String name, RubyModule implClass) {
        if (runtime.hasEventHooks()) context.trace(RubyEvent.B_CALL, name, implClass);
    }

    private static void blockPostTrace(Ruby runtime, ThreadContext context, String name, RubyModule implClass) {
        if (runtime.hasEventHooks()) context.trace(RubyEvent.B_RETURN, name, implClass);
    }
    
    /**
     * Evaluate the given string under the specified binding object. If the binding is not a Proc or Binding object
     * (RubyProc or RubyBinding) throw an appropriate type error.
     * @param context the thread context for the current thread
     * @param self the self against which eval was called; used as self in the eval in 1.9 mode
     * @param src The string containing the text to be evaluated
     * @param binding The binding object under which to perform the evaluation
     * @return An IRubyObject result from the evaluation
     */
    public static IRubyObject evalWithBinding(ThreadContext context, IRubyObject self, IRubyObject src, Binding binding) {
        Ruby runtime = src.getRuntime();
        DynamicScope evalScope;

        // in 1.9, eval scopes are local to the binding
        evalScope = binding.getEvalScope(runtime);
        evalScope.setEvalType(EvalType.BINDING_EVAL);

        // FIXME:  This determine module is in a strange location and should somehow be in block
        evalScope.getStaticScope().determineModule();

        Frame lastFrame = context.preEvalWithBinding(binding);
        try {
            // Binding provided for scope, use it
            RubyString source = src.convertToString();
            Node node = runtime.parseEval(source.getByteList(), binding.getFile(), evalScope, binding.getLine());
            Block block = binding.getFrame().getBlock();

            if (runtime.getInstanceConfig().getCompileMode() == CompileMode.TRUFFLE) {
                throw new UnsupportedOperationException();
            }

            // SSS FIXME: AST interpreter passed both a runtime (which comes from the source string)
            // and the thread-context rather than fetch one from the other.  Why is that?
            return Interpreter.interpretBindingEval(runtime, binding.getFile(), binding.getLine(), binding.getMethod(), node, self, block);
        } catch (JumpException.BreakJump bj) {
            throw runtime.newLocalJumpError(RubyLocalJumpError.Reason.BREAK, (IRubyObject)bj.getValue(), "unexpected break");
        } catch (JumpException.RedoJump rj) {
            throw runtime.newLocalJumpError(RubyLocalJumpError.Reason.REDO, (IRubyObject)rj.getValue(), "unexpected redo");
        } catch (StackOverflowError soe) {
            throw runtime.newSystemStackError("stack level too deep", soe);
        } finally {
            context.postEvalWithBinding(binding, lastFrame);
        }
    }
    
    public static Block getBlock(Ruby runtime, ThreadContext context, IRubyObject self, Block currentBlock, Node blockNode) {
        if (blockNode == null) return Block.NULL_BLOCK;
        
        if (blockNode instanceof IterNode) {
            return null;
        } else if (blockNode instanceof BlockPassNode) {
            return getBlockPassBlock(blockNode, runtime,context, self, currentBlock);
        }
         
        assert false: "Trying to get block from something which cannot deliver";
        return null;
    }

    private static Block getBlockPassBlock(Node blockNode, Ruby runtime, ThreadContext context, IRubyObject self, Block currentBlock) {
        Node bodyNode = ((BlockPassNode) blockNode).getBodyNode();
        IRubyObject proc;
        if (bodyNode == null) {
            proc = runtime.getNil();
        } else {
            proc = null;
        }

        return Helpers.getBlockFromBlockPassBody(proc, currentBlock);
    }

    public static RubyModule getClassVariableBase(Ruby runtime, StaticScope scope) {
        RubyModule rubyClass = scope.getModule();
        while (rubyClass.isSingleton() || rubyClass == runtime.getDummy()) {
            scope = scope.getPreviousCRefScope();
            rubyClass = scope.getModule();
            if (scope.getPreviousCRefScope() == null) {
                runtime.getWarnings().warn(ID.CVAR_FROM_TOPLEVEL_SINGLETON_METHOD, "class variable access from toplevel singleton method");
            }
        }
        return rubyClass;
    }
}
