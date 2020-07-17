// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.errorprone.xplat.checker;

import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;

import com.google.common.io.Resources;
import com.google.errorprone.BugPattern;
import com.google.errorprone.ErrorProneFlags;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.ImportTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.MethodTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.NewClassTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.VariableTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;
import com.google.common.base.Charsets;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import org.json.JSONException;
import org.json.JSONObject;


/**
 * Check for usage of some Joda-Time classes and packages, which can be found in
 * resources/Xplatbans.json. These calls are banned due to their incompatibility with cross platform
 * development. Additional classes can be banned using the command line argument {@code
 * -XepOpt:XplatClassBan:JSON=X}, where X is the path to a JSON file containing custom bans.
 */
@BugPattern(
    name = "XplatBans",
    summary = "Bans the usage of certain Joda-Time classes and packages for cross platform use.",
    explanation =
        "The usage of several Joda-Time classes and packages are banned from cross"
            + " platform development due to incompatibilities. They are unsupported on the web"
            + " and should also not be used on supported platforms.",
    severity = ERROR)
public class XplatBans extends BugChecker
    implements MethodInvocationTreeMatcher, NewClassTreeMatcher, ImportTreeMatcher,
    VariableTreeMatcher, MethodTreeMatcher {

  private final Map<String, String> packageNames = new HashMap<>();

  private final Map<String, String> classNames = new HashMap<>();

  private final Map<String, Map<String, String>> methodNames = new HashMap<>();


  private void getJsonData(String json, String fileName) {
    JSONObject obj;

    try {
      obj = new JSONObject(json);
    } catch (JSONException e) {
      System.err.println(String.format("JSON file '%s' is invalid. Unable to parse.", fileName));
      return;
    }

    try {
      JSONObject classes = obj.getJSONObject("classes");

      for (Iterator it = classes.keys(); it.hasNext(); ) {
        String key = it.next().toString();
        this.classNames.put(key, classes.getString(key));
      }
    } catch (JSONException e) {
      System.err
          .println(String.format("Missing \"classes\" top level JSON name inside '%s'.", fileName));
    }

    try {
      JSONObject packages = obj.getJSONObject("packages");

      for (Iterator it = packages.keys(); it.hasNext(); ) {
        String key = it.next().toString();

        this.packageNames.put(key, packages.getString(key));
      }
    } catch (JSONException e) {
      System.err
          .println(
              String.format("Missing \"packages\" top level JSON name inside '%s'.", fileName));
    }

    try {
      JSONObject containingClasses = obj.getJSONObject("methods");

      for (Iterator cont = containingClasses.keys(); cont.hasNext(); ) {
        String curClass = cont.next().toString();
        Map<String, String> localMap = new HashMap<>();

        JSONObject methods = containingClasses.getJSONObject(curClass);

        for (Iterator it = methods.keys(); it.hasNext(); ) {
          String key = it.next().toString();
          localMap.put(key, methods.getString(key));
        }

        this.methodNames.put(curClass, localMap);
      }
    } catch (JSONException e) {
      System.err
          .println(String.format("Missing \"methods\" top level JSON name inside '%s'.", fileName));
    }


  }

  public XplatBans(ErrorProneFlags flags) {
    try {
      getJsonData(Resources.toString(Resources.getResource("Xplatbans.json"), Charsets.UTF_8),
          "Xplatbans.json");
    } catch (IOException e) {
      System.err.println("Xplatbans.json resource file for XplatClassBan checker could not"
          + " be converted to a String.");
      throw new UncheckedIOException(e);
    } catch (IllegalArgumentException e) {
      System.err.println("Xplatbans.json resource file for XplatClassBan checker could not"
          + " be found.");
      throw new IllegalArgumentException(e);
    }

    Optional<String> arg = flags.get("XplatBans:JSON");

    if (arg.isPresent()) {
      try {
        getJsonData(Files.readString(Paths.get(arg.get()), Charsets.UTF_8), arg.get());
      } catch (IOException e) {
        System.err.println("JSON file argument for JodaTimeClassBan checker could not"
            + " be found/read. Custom bans will not be in effect.");
      }
    }
  }

  public Description standardMessage(Tree tree, String target, String reason) {
    if (reason.length() == 0) {
      reason = "cross platform incompatibility.";
    }

    return buildDescription(tree)
        .setMessage(
            String.format("Use of %s has been banned due to %s", target, reason))
        .build();
  }

  public Description methodCallMessage(Tree tree, String method, String target, String reason) {
    if (reason.length() == 0) {
      reason = "cross platform incompatibility.";
    }

    return buildDescription(tree)
        .setMessage(
            String.format("Use of %s is not allowed, as %s has been banned due to %s", method,
                target, reason))
        .build();
  }

  public Description constructorMessage(Tree tree, String constructor, String target,
      String reason) {
    if (reason.length() == 0) {
      reason = "cross platform incompatibility.";
    }

    return buildDescription(tree)
        .setMessage(
            String.format(
                "Use of this constructor (%s) is not allowed, as %s"
                    + " is banned due to %s", constructor, target, reason))
        .build();
  }

  private String typeToString(Type type) {
    if (type == null) {
      return "nullType";
    }
    String typeString = type.toString();

    if (typeString.contains("<")) {
      return typeString.substring(0, typeString.indexOf("<"));
    }
    return typeString;
  }

  @Override
  public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {

    Symbol methodSymbol = ASTHelpers.getSymbol(tree);
    String methodRecvType = typeToString(ASTHelpers.getReceiverType(tree));
    String methodType = typeToString(ASTHelpers.getType(tree));

    if (methodSymbol != null) {

      // checks receiver for banned classes/packages
      if (classNames.containsKey(methodRecvType)) {
        return standardMessage(tree, methodRecvType,
            classNames.get(methodRecvType));
      } else if (packageNames.containsKey(methodSymbol.packge().toString())) {
        return standardMessage(tree, methodSymbol.packge().toString(),
            packageNames.get(methodSymbol.packge().toString()));
      }

      // checks if method was banned directly
      if (methodNames.containsKey(methodRecvType) && methodNames.get(methodRecvType)
          .containsKey(methodSymbol.getSimpleName().toString())) {
        return methodCallMessage(tree, methodSymbol.getSimpleName().toString() + "()",
            methodRecvType,
            methodNames.get(methodRecvType).get(methodSymbol.getSimpleName().toString()));
      }

      // checks caller for banned classes
      if (classNames.containsKey(methodType)) {
        return methodCallMessage(tree, methodSymbol.toString(), methodType,
            classNames.get(methodType));
      }

      // checks caller for banned packages
      for (String pack_name : packageNames.keySet()) {
        if (methodType.startsWith(pack_name)) {
          return methodCallMessage(tree, methodSymbol.toString(), pack_name,
              packageNames.get(pack_name));
        }
      }
    }

    // checks arguments for banned classes/packages
    for (ExpressionTree arg : tree.getArguments()) {
      Symbol argSymbol = ASTHelpers.getSymbol(arg);
      String argType = typeToString(ASTHelpers.getType(arg));

      if (argSymbol != null && methodSymbol != null) {
        if (classNames.containsKey(argType)) {
          return standardMessage(tree, methodSymbol.toString(),
              classNames.get(argType));
        } else if (packageNames.containsKey(argSymbol.packge().toString())) {
          return standardMessage(tree, methodSymbol.toString(),
              packageNames.get(argSymbol.packge().toString()));
        }
      }
    }

    return Description.NO_MATCH;
  }

  @Override
  public Description matchNewClass(NewClassTree tree, VisitorState state) {

    MethodSymbol constructorSymbol = ASTHelpers.getSymbol(tree);
    String constructorType = typeToString(ASTHelpers.getType(tree));

    if (constructorSymbol != null) {
      // checks constructor for banned classes/packages
      if (classNames.containsKey(constructorType)) {
        return standardMessage(tree, constructorType,
            classNames.get(constructorType));
      } else if (packageNames.containsKey(constructorSymbol.packge().toString())) {
        return standardMessage(tree, constructorSymbol.packge().toString(),
            packageNames.get(constructorSymbol.packge().toString()));
      }

      // checks parameters for banned classes/packages
      for (VarSymbol param : constructorSymbol.getParameters()) {
        String paramType = typeToString(param.type);

        if (classNames.containsKey(paramType)) {
          return constructorMessage(tree, constructorSymbol.toString(), paramType,
              classNames.get(paramType));
        } else if (packageNames.containsKey(param.packge().toString())) {
          return constructorMessage(tree, constructorSymbol.toString(),
              param.packge().toString(), packageNames.get(param.packge().toString()));
        }
      }
    }
    return Description.NO_MATCH;
  }

  @Override
  public Description matchImport(ImportTree tree, VisitorState state) {
    Symbol importSymbol = ASTHelpers.getSymbol(tree.getQualifiedIdentifier());

    if (classNames.containsKey(importSymbol.toString())) {
      return standardMessage(tree, importSymbol.toString(),
          classNames.get(importSymbol.toString()));
    } else if (packageNames.containsKey(importSymbol.packge().toString())) {
      return standardMessage(tree, importSymbol.packge().toString(),
          packageNames.get(importSymbol.packge().toString()));
    }

    return Description.NO_MATCH;
  }

  @Override
  public Description matchVariable(VariableTree tree, VisitorState state) {
    String varType = typeToString(ASTHelpers.getType(tree));

    if (classNames.containsKey(varType)) {
      return standardMessage(tree, varType, classNames.get(varType));
    }

    for (String packName : packageNames.keySet()) {
      if (varType.startsWith(packName)) {
        return standardMessage(tree, packName, packageNames.get(packName));
      }
    }

    return Description.NO_MATCH;
  }

  @Override
  public Description matchMethod(MethodTree tree, VisitorState state) {
    Type type = ASTHelpers.getType(tree);

    if (type != null) {
      String methodType = typeToString(type.getReturnType());
      if (classNames.containsKey(methodType)) {
        return standardMessage(tree, methodType, classNames.get(methodType));
      }

      for (String packName : packageNames.keySet()) {
        if (methodType.startsWith(packName)) {
          return standardMessage(tree, packName, packageNames.get(packName));
        }
      }
    }
    return Description.NO_MATCH;
  }
}
