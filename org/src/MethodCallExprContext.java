/*
 * Copyright (C) 2015-2016 Federico Tomassetti
 * Copyright (C) 2017-2020 The JavaParser Team.
 *
 * This file is part of JavaParser.
 *
 * JavaParser can be used either under the terms of
 * a) the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * b) the terms of the Apache License
 *
 * You should have received a copy of both licenses in LICENCE.LGPL and
 * LICENCE.APACHE. Please refer to those files for details.
 *
 * JavaParser is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 */

package com.github.javaparser.symbolsolver.javaparsermodel.contexts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.resolution.MethodUsage;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedParameterDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedTypeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedTypeParameterDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.types.ResolvedArrayType;
import com.github.javaparser.resolution.types.ResolvedLambdaConstraintType;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.resolution.types.ResolvedTypeVariable;
import com.github.javaparser.resolution.types.ResolvedUnionType;
import com.github.javaparser.resolution.types.ResolvedWildcard;
import com.github.javaparser.symbolsolver.core.resolution.Context;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.model.resolution.SymbolReference;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.model.resolution.Value;
import com.github.javaparser.symbolsolver.model.typesystem.ReferenceTypeImpl;
import com.github.javaparser.symbolsolver.reflectionmodel.MyObjectProvider;
import com.github.javaparser.symbolsolver.reflectionmodel.ReflectionClassDeclaration;
import com.github.javaparser.symbolsolver.resolution.MethodResolutionLogic;
import com.github.javaparser.utils.Pair;

public class MethodCallExprContext extends AbstractJavaParserContext<MethodCallExpr> {

    ///
    /// Constructors
    ///

    public MethodCallExprContext(MethodCallExpr wrappedNode, TypeSolver typeSolver) {
        super(wrappedNode, typeSolver);
    }

    ///
    /// Public methods
    ///

    @Override
    public Optional<ResolvedType> solveGenericType(String name) {
        Optional<Expression> nodeScope = wrappedNode.getScope();
        if (!nodeScope.isPresent()) {
            return Optional.empty();
        }

        // Method calls can have generic types defined, for example: {@code expr.<T1, T2>method(x, y, z);} or {@code super.<T, E>check2(val1, val2).}
        ResolvedType typeOfScope = JavaParserFacade.get(typeSolver).getType(nodeScope.get());
        Optional<ResolvedType> resolvedType = typeOfScope.asReferenceType().getGenericParameterByName(name);

        // TODO/FIXME: Consider if we should check if the result is present, else delegate "up" the context chain (e.g. {@code solveGenericTypeInParent()})
        return resolvedType;
    }

    @Override
    public String toString() {
        return "MethodCallExprContext{wrapped=" + wrappedNode + "}";
    }

    @Override
    public Optional<MethodUsage> solveMethodAsUsage(String name, List<ResolvedType> argumentsTypes) {
        ResolvedType typeOfScope;
        if (wrappedNode.getScope().isPresent()) {
            Expression scope = wrappedNode.getScope().get();
            // Consider static method calls
            if (scope instanceof NameExpr) {
                String className = ((NameExpr) scope).getName().getId();
                SymbolReference<ResolvedTypeDeclaration> ref = solveType(className);
                if (ref.isSolved()) {
                    SymbolReference<ResolvedMethodDeclaration> m = MethodResolutionLogic.solveMethodInType(ref.getCorrespondingDeclaration(), name, argumentsTypes);
                    if (m.isSolved()) {
                        MethodUsage methodUsage = new MethodUsage(m.getCorrespondingDeclaration());
                        methodUsage = resolveMethodTypeParametersFromExplicitList(typeSolver, methodUsage);
                        methodUsage = resolveMethodTypeParameters(methodUsage, argumentsTypes);
                        return Optional.of(methodUsage);
                    } else {
                        throw new UnsolvedSymbolException(ref.getCorrespondingDeclaration().toString(),
                                "Method '" + name + "' with parameterTypes " + argumentsTypes);
                    }
                }
            }

            // Scope is present -- search/solve within that type
            typeOfScope = JavaParserFacade.get(typeSolver).getType(scope);
        } else {
            // Scope not present -- search/solve within itself.
            typeOfScope = JavaParserFacade.get(typeSolver).getTypeOfThisIn(wrappedNode);
        }

        // we can replace the parameter types from the scope into the typeParametersValues
        Map<ResolvedTypeParameterDeclaration, ResolvedType> inferredTypes = new HashMap<>();
        for (int i = 0; i < argumentsTypes.size(); i++) {
            // by replacing types I can also find new equivalences
            // for example if I replace T=U with String because I know that T=String I can derive that also U equal String
            ResolvedType originalArgumentType = argumentsTypes.get(i);
            ResolvedType updatedArgumentType = usingParameterTypesFromScope(typeOfScope, originalArgumentType, inferredTypes);
            argumentsTypes.set(i, updatedArgumentType);
        }
        for (int i = 0; i < argumentsTypes.size(); i++) {
            ResolvedType updatedArgumentType = applyInferredTypes(argumentsTypes.get(i), inferredTypes);
            argumentsTypes.set(i, updatedArgumentType);
        }

        return solveMethodAsUsage(typeOfScope, name, argumentsTypes, this);
    }

    private MethodUsage resolveMethodTypeParametersFromExplicitList(TypeSolver typeSolver, MethodUsage methodUsage) {
        if (wrappedNode.getTypeArguments().isPresent()) {
            final List<ResolvedType> typeArguments = new ArrayList<>();
            for (com.github.javaparser.ast.type.Type ty : wrappedNode.getTypeArguments().get()) {
                typeArguments.add(JavaParserFacade.get(typeSolver).convertToUsage(ty));
            }

            List<ResolvedTypeParameterDeclaration> tyParamDecls = methodUsage.getDeclaration().getTypeParameters();
            if (tyParamDecls.size() == typeArguments.size()) {
                for (int i = 0; i < tyParamDecls.size(); i++) {
                    methodUsage = methodUsage.replaceTypeParameter(tyParamDecls.get(i), typeArguments.get(i));
                }
            }
        }

        return methodUsage;
    }

    @Override
    public Optional<Value> solveSymbolAsValue(String name) {
        return solveSymbolAsValueInParentContext(name);
    }

    @Override
    public SymbolReference<ResolvedMethodDeclaration> solveMethod(String name, List<ResolvedType> argumentsTypes, boolean staticOnly) {
        Collection<ResolvedReferenceTypeDeclaration> rrtds = findTypeDeclarations(wrappedNode.getScope());

        if (rrtds.isEmpty()) {
            // if the bounds of a type parameter are empty, then the bound is implicitly "extends Object"
            // we don't make this _ex_plicit in the data representation because that would affect codegen
            // and make everything generate like <T extends Object> instead of <T>
            // https://github.com/javaparser/javaparser/issues/2044
            rrtds = Collections.singleton(typeSolver.getSolvedJavaLangObject());
        }

        for (ResolvedReferenceTypeDeclaration rrtd : rrtds) {
            SymbolReference<ResolvedMethodDeclaration> res = MethodResolutionLogic.solveMethodInType(rrtd, name, argumentsTypes, false);
            if (res.isSolved()) {
                return res;
            }
        }

        return SymbolReference.unsolved(ResolvedMethodDeclaration.class);
    }

    ///
    /// Private methods
    ///

    private Optional<MethodUsage> solveMethodAsUsage(ResolvedReferenceType refType, String name,
                                                     List<ResolvedType> argumentsTypes,
                                                     Context invokationContext) {
        if(!refType.getTypeDeclaration().isPresent()) {
            return Optional.empty();
        }

        Optional<MethodUsage> ref = ContextHelper.solveMethodAsUsage(refType.getTypeDeclaration().get(), name, argumentsTypes, invokationContext, refType.typeParametersValues());
        if (ref.isPresent()) {
            MethodUsage methodUsage = ref.get();

            methodUsage = resolveMethodTypeParametersFromExplicitList(typeSolver, methodUsage);

            // At this stage I should derive from the context and the value some information on the type parameters
            // for example, when calling:
            // myStream.collect(Collectors.toList())
            // I should be able to figure out that considering the type of the stream (e.g., Stream<String>)
            // and considering that Stream has this method:
            //
            // <R,A> R collect(Collector<? super T,A,R> collector)
            //
            // and collector has this method:
            //
            // static <T> Collector<T,?,List<T>>   toList()
            //
            // In this case collect.R has to be equal to List<toList.T>
            // And toList.T has to be equal to ? super Stream.T
            // Therefore R has to be equal to List<? super Stream.T>.
            // In our example Stream.T equal to String, so the R (and the result of the call to collect) is
            // List<? super String>

            Map<ResolvedTypeParameterDeclaration, ResolvedType> derivedValues = new HashMap<>();
            for (int i = 0; i < methodUsage.getParamTypes().size(); i++) {
                ResolvedParameterDeclaration parameter = methodUsage.getDeclaration().getParam(i);
                ResolvedType parameterType = parameter.getType();
                // Don't continue if a vararg parameter is reached and there are no arguments left
                if (parameter.isVariadic() && argumentsTypes.size() < methodUsage.getNoParams()) {
                    break;
                }
                if (!argumentsTypes.get(i).isArray() && parameter.isVariadic()) {
                    parameterType = parameterType.asArrayType().getComponentType();
                }
                inferTypes(argumentsTypes.get(i), parameterType, derivedValues);
            }

            for (Map.Entry<ResolvedTypeParameterDeclaration, ResolvedType> entry : derivedValues.entrySet()){
                methodUsage = methodUsage.replaceTypeParameter(entry.getKey(), entry.getValue());
            }

            ResolvedType returnType = refType.useThisTypeParametersOnTheGivenType(methodUsage.returnType());
            if (returnType != methodUsage.returnType()) {
                methodUsage = methodUsage.replaceReturnType(returnType);
            }
            for (int i = 0; i < methodUsage.getParamTypes().size(); i++) {
                ResolvedType replaced = refType.useThisTypeParametersOnTheGivenType(methodUsage.getParamTypes().get(i));
                methodUsage = methodUsage.replaceParamType(i, replaced);
            }
            return Optional.of(methodUsage);
        } else {
            return ref;
        }
    }

    private void inferTypes(ResolvedType source, ResolvedType target, Map<ResolvedTypeParameterDeclaration, ResolvedType> mappings) {
        if (source.equals(target)) {
            return;
        }
        if (source.isReferenceType() && target.isReferenceType()) {
            ResolvedReferenceType sourceRefType = source.asReferenceType();
            ResolvedReferenceType targetRefType = target.asReferenceType();
            if (sourceRefType.getQualifiedName().equals(targetRefType.getQualifiedName())) {
                if (!sourceRefType.isRawType() && !targetRefType.isRawType()) {
                    for (int i = 0; i < sourceRefType.typeParametersValues().size(); i++) {
                        inferTypes(sourceRefType.typeParametersValues().get(i), targetRefType.typeParametersValues().get(i), mappings);
                    }
                }
            }
            return;
        }
        if (source.isReferenceType() && target.isWildcard()) {
            if (target.asWildcard().isBounded()) {
                inferTypes(source, target.asWildcard().getBoundedType(), mappings);
                return;
            }
            return;
        }
        if (source.isWildcard() && target.isWildcard()) {
            if (source.asWildcard().isBounded() && target.asWildcard().isBounded()){
                inferTypes(source.asWildcard().getBoundedType(), target.asWildcard().getBoundedType(), mappings);
            }
            return;
        }
        if (source.isReferenceType() && target.isTypeVariable()) {
            mappings.put(target.asTypeParameter(), source);
            return;
        }
        if (source.isWildcard() && target.isTypeVariable()) {
            mappings.put(target.asTypeParameter(), source);
            return;
        }
        if (source.isArray() && target.isArray()) {
            ResolvedType sourceComponentType = source.asArrayType().getComponentType();
            ResolvedType targetComponentType = target.asArrayType().getComponentType();
            inferTypes(sourceComponentType, targetComponentType, mappings);
            return;
        }
        if (source.isArray() && target.isWildcard()){
            if(target.asWildcard().isBounded()){
                inferTypes(source, target.asWildcard().getBoundedType(), mappings);
                return;
            }
            return;
        }
        if (source.isArray() && target.isTypeVariable()) {
            mappings.put(target.asTypeParameter(), source);
            return;
        }

        if (source.isWildcard() && target.isReferenceType()){
            if (source.asWildcard().isBounded()){
                inferTypes(source.asWildcard().getBoundedType(), target, mappings);
            }
            return;
        }
        if (source.isConstraint() && target.isReferenceType()){
            inferTypes(source.asConstraintType().getBound(), target, mappings);
            return;
        }

        if (source.isConstraint() && target.isTypeVariable()){
            inferTypes(source.asConstraintType().getBound(), target, mappings);
            return;
        }
        if (source.isTypeVariable() && target.isTypeVariable()) {
            mappings.put(target.asTypeParameter(), source);
            return;
        }
        if (source.isTypeVariable()) {
            inferTypes(target, source, mappings);
            return;
        }
        if (source.isPrimitive() || target.isPrimitive()) {
            return;
        }
        if (source.isNull()) {
            return;
        }
        throw new RuntimeException(source.describe() + " " + target.describe());
    }

    private MethodUsage resolveMethodTypeParameters(MethodUsage methodUsage, List<ResolvedType> actualParamTypes) {
        Map<ResolvedTypeParameterDeclaration, ResolvedType> matchedTypeParameters = new HashMap<>();

        if (methodUsage.getDeclaration().hasVariadicParameter()) {
            if (actualParamTypes.size() == methodUsage.getDeclaration().getNumberOfParams()) {
                // the varargs parameter is an Array, so extract the inner type
                ResolvedType expectedType =
                        methodUsage.getDeclaration().getLastParam().getType().asArrayType().getComponentType();
                // the varargs corresponding type can be either T or Array<T>
                ResolvedType actualType =
                        actualParamTypes.get(actualParamTypes.size() - 1).isArray() ?
                                actualParamTypes.get(actualParamTypes.size() - 1).asArrayType().getComponentType() :
                                actualParamTypes.get(actualParamTypes.size() - 1);
                if (!expectedType.isAssignableBy(actualType)) {
                    for (ResolvedTypeParameterDeclaration tp : methodUsage.getDeclaration().getTypeParameters()) {
                        expectedType = MethodResolutionLogic.replaceTypeParam(expectedType, tp, typeSolver);
                    }
                }
                if (!expectedType.isAssignableBy(actualType)) {
                    // ok, then it needs to be wrapped
                    throw new UnsupportedOperationException(
                            String.format("Unable to resolve the type typeParametersValues in a MethodUsage. Expected type: %s, Actual type: %s. Method Declaration: %s. MethodUsage: %s",
                                    expectedType,
                                    actualType,
                                    methodUsage.getDeclaration(),
                                    methodUsage));
                }
                // match only the varargs type
                matchTypeParameters(expectedType, actualType, matchedTypeParameters);
            } else {
                return methodUsage;
            }
        }

        int until = methodUsage.getDeclaration().hasVariadicParameter() ?
                actualParamTypes.size() - 1 :
                actualParamTypes.size();

        for (int i = 0; i < until; i++) {
            ResolvedType expectedType = methodUsage.getParamType(i);
            ResolvedType actualType = actualParamTypes.get(i);
            matchTypeParameters(expectedType, actualType, matchedTypeParameters);
        }
        for (ResolvedTypeParameterDeclaration tp : matchedTypeParameters.keySet()) {
            methodUsage = methodUsage.replaceTypeParameter(tp, matchedTypeParameters.get(tp));
        }
        return methodUsage;
    }

    private void matchTypeParameters(ResolvedType expectedType, ResolvedType actualType, Map<ResolvedTypeParameterDeclaration, ResolvedType> matchedTypeParameters) {
        if (expectedType.isTypeVariable()) {
            ResolvedType type = actualType;
            // in case of primitive type, the expected type must be compared with the boxed type of the actual type
            if (type.isPrimitive()) {
                type = MyObjectProvider.INSTANCE.byName(type.asPrimitive().getBoxTypeQName());
            }
            if (!type.isTypeVariable() && !type.isReferenceType()) {
                throw new UnsupportedOperationException(type.getClass().getCanonicalName());
            }
            matchedTypeParameters.put(expectedType.asTypeParameter(), type);
        } else if (expectedType.isArray()) {
            // Issue 2258 : NullType must not fail this search
            if (!(actualType.isArray() || actualType.isNull())) {
                throw new UnsupportedOperationException(actualType.getClass().getCanonicalName());
            }
            matchTypeParameters(
                    expectedType.asArrayType().getComponentType(),
                    actualType.isNull() ? actualType : actualType.asArrayType().getComponentType(),
                    matchedTypeParameters);
        } else if (expectedType.isReferenceType()) {
            // avoid cases where the actual type has no type parameters but the expected one has. Such as: "classX extends classY<Integer>"
            if (actualType.isReferenceType() && actualType.asReferenceType().typeParametersValues().size() > 0) {
                int i = 0;
                for (ResolvedType tp : expectedType.asReferenceType().typeParametersValues()) {
                    matchTypeParameters(tp, actualType.asReferenceType().typeParametersValues().get(i), matchedTypeParameters);
                    i++;
                }
            }
        } else if (expectedType.isPrimitive()) {
            // nothing to do
        } else if (expectedType.isWildcard()) {
            // nothing to do
        } else {
            throw new UnsupportedOperationException(expectedType.getClass().getCanonicalName());
        }
    }

    private Optional<MethodUsage> solveMethodAsUsage(ResolvedTypeVariable tp, String name, List<ResolvedType> argumentsTypes, Context invokationContext) {
        List<ResolvedTypeParameterDeclaration.Bound> bounds = tp.asTypeParameter().getBounds();

        if (bounds.isEmpty()) {
            // if the bounds of a type parameter are empty, then the bound is implicitly "extends Object"
            // we don't make this _ex_plicit in the data representation because that would affect codegen
            // and make everything generate like <T extends Object> instead of <T>
            // https://github.com/javaparser/javaparser/issues/2044
            bounds = Collections.singletonList(
                    ResolvedTypeParameterDeclaration.Bound.extendsBound(
                            JavaParserFacade.get(typeSolver).classToResolvedType(Object.class)));
        }

        for (ResolvedTypeParameterDeclaration.Bound bound : bounds) {
            Optional<MethodUsage> methodUsage = solveMethodAsUsage(bound.getType(), name, argumentsTypes, invokationContext);
            if (methodUsage.isPresent()) {
                return methodUsage;
            }
        }

        return Optional.empty();
    }

    private Optional<MethodUsage> solveMethodAsUsage(ResolvedType type, String name, List<ResolvedType> argumentsTypes, Context invokationContext) {
        if (type instanceof ResolvedReferenceType) {
            return solveMethodAsUsage((ResolvedReferenceType) type, name, argumentsTypes, invokationContext);
        } else if (type instanceof ResolvedTypeVariable) {
            return solveMethodAsUsage((ResolvedTypeVariable) type, name, argumentsTypes, invokationContext);
        } else if (type instanceof ResolvedWildcard) {
            ResolvedWildcard wildcardUsage = (ResolvedWildcard) type;
            if (wildcardUsage.isSuper()) {
                return solveMethodAsUsage(wildcardUsage.getBoundedType(), name, argumentsTypes, invokationContext);
            } else if (wildcardUsage.isExtends()) {
                return solveMethodAsUsage(wildcardUsage.getBoundedType(), name, argumentsTypes, invokationContext);
            } else {
                return solveMethodAsUsage(new ReferenceTypeImpl(new ReflectionClassDeclaration(Object.class, typeSolver), typeSolver), name, argumentsTypes, invokationContext);
            }
        } else if (type instanceof ResolvedLambdaConstraintType){
            ResolvedLambdaConstraintType constraintType = (ResolvedLambdaConstraintType) type;
            return solveMethodAsUsage(constraintType.getBound(), name, argumentsTypes, invokationContext);
        } else if (type instanceof ResolvedArrayType) {
            // An array inherits methods from Object not from it's component type
            return solveMethodAsUsage(new ReferenceTypeImpl(new ReflectionClassDeclaration(Object.class, typeSolver), typeSolver), name, argumentsTypes, invokationContext);
        } else if (type instanceof ResolvedUnionType) {
            Optional<ResolvedReferenceType> commonAncestor = type.asUnionType().getCommonAncestor();
            if (commonAncestor.isPresent()) {
                return solveMethodAsUsage(commonAncestor.get(), name, argumentsTypes, invokationContext);
            } else {
                throw new UnsupportedOperationException("no common ancestor available for " + type.describe());
            }
        } else {
            throw new UnsupportedOperationException("type usage: " + type.getClass().getCanonicalName());
        }
    }

    private ResolvedType usingParameterTypesFromScope(ResolvedType scope, ResolvedType type, Map<ResolvedTypeParameterDeclaration, ResolvedType> inferredTypes) {
        if (type.isReferenceType()) {
            for (Pair<ResolvedTypeParameterDeclaration, ResolvedType> entry : type.asReferenceType().getTypeParametersMap()) {
                if (entry.a.declaredOnType() && scope.asReferenceType().getGenericParameterByName(entry.a.getName()).isPresent()) {
                    type = type.replaceTypeVariables(entry.a, scope.asReferenceType().getGenericParameterByName(entry.a.getName()).get(), inferredTypes);
                }
            }
            return type;
        } else {
            return type;
        }
    }

    private ResolvedType applyInferredTypes(ResolvedType type, Map<ResolvedTypeParameterDeclaration, ResolvedType> inferredTypes) {
        for (ResolvedTypeParameterDeclaration tp : inferredTypes.keySet()) {
            type = type.replaceTypeVariables(tp, inferredTypes.get(tp), inferredTypes);
        }
        return type;
    }
}
