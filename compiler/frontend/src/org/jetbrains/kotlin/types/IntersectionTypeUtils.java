/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.types;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.builtins.KotlinBuiltIns;
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor;
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor;
import org.jetbrains.kotlin.resolve.calls.inference.CallHandle;
import org.jetbrains.kotlin.resolve.calls.inference.ConstraintSystem;
import org.jetbrains.kotlin.resolve.calls.inference.ConstraintSystemBuilderImpl;
import org.jetbrains.kotlin.types.checker.IntersectionTypeKt;
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker;

import java.util.*;

import static org.jetbrains.kotlin.resolve.calls.inference.constraintPosition.ConstraintPositionKind.SPECIAL;
import static org.jetbrains.kotlin.resolve.descriptorUtil.DescriptorUtilsKt.getBuiltIns;

public class IntersectionTypeUtils {

    public static boolean isIntersectionTypePopulatedOrNothing(@NotNull KotlinType typeA, @NotNull KotlinType typeB) {
        KotlinType intersectionType = IntersectionTypeKt.intersectWrappedTypes(new LinkedHashSet<>(Arrays.asList(typeA, typeB)));
        return isTypePopulatedOrNothing(intersectionType);
    }

    public static boolean isTypePopulatedOrNothing(@NotNull KotlinType type) {
        return KotlinBuiltIns.isNothing(type) || isTypePopulated(type);
    }

    private static boolean isTypePopulated(@NotNull KotlinType type) {
        // For type (L..U) we should check if only U is actually populated, because L can be not populated.
        // The simplest such type is dynamic type
        return isTypePopulated(FlexibleTypesKt.upperIfFlexible(type));
    }

    private static boolean isTypePopulated(@NotNull SimpleType type) {
        if (!(type.getConstructor() instanceof IntersectionTypeConstructor)) {
            return !KotlinBuiltIns.isNothing(type);
        }

        Collection<KotlinType> typesInIntersection = type.getConstructor().getSupertypes();

        KotlinTypeChecker typeChecker = KotlinTypeChecker.DEFAULT;
        for (KotlinType lower : typesInIntersection) {
            if (TypeUtils.canHaveSubtypes(typeChecker, lower)) {
                continue;
            }

            for (KotlinType upper : typesInIntersection) {
                boolean mayBeEqual = TypeUnifier.mayBeEqual(lower, upper);
                if (!mayBeEqual && !typeChecker.isSubtypeOf(lower, upper) && !typeChecker.isSubtypeOf(upper, lower)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Note: this method was used in overload and override bindings to approximate type parameters with several bounds,
     * but as it turned out at some point, that logic was inconsistent with Java rules, so it was simplified.
     * Most of the other usages of this method are left untouched but probably should be investigated closely if they're still valid.
     */
    @NotNull
    public static KotlinType getUpperBoundsAsType(@NotNull TypeParameterDescriptor descriptor) {
        List<KotlinType> upperBounds = descriptor.getUpperBounds();
        assert !upperBounds.isEmpty() : "Upper bound list is empty: " + descriptor;
        KotlinType upperBoundsAsType = IntersectionTypeKt.intersectWrappedTypes(upperBounds);
        return isTypePopulatedOrNothing(upperBoundsAsType) ? upperBoundsAsType : getBuiltIns(descriptor).getNothingType();
    }

    private static class TypeUnifier {
        private static class TypeParameterUsage {
            private final TypeParameterDescriptor typeParameterDescriptor;
            private final Variance howTheTypeParameterIsUsed;

            public TypeParameterUsage(TypeParameterDescriptor typeParameterDescriptor, Variance howTheTypeParameterIsUsed) {
                this.typeParameterDescriptor = typeParameterDescriptor;
                this.howTheTypeParameterIsUsed = howTheTypeParameterIsUsed;
            }
        }

        public static boolean mayBeEqual(@NotNull KotlinType type, @NotNull KotlinType other) {
            return unify(type, other);
        }

        private static boolean unify(KotlinType withParameters, KotlinType expected) {
            // T -> how T is used
            Map<TypeParameterDescriptor, Variance> parameters = new HashMap<>();
            Function1<TypeParameterUsage, Unit> processor = parameterUsage -> {
                Variance howTheTypeIsUsedBefore = parameters.get(parameterUsage.typeParameterDescriptor);
                if (howTheTypeIsUsedBefore == null) {
                    howTheTypeIsUsedBefore = Variance.INVARIANT;
                }
                parameters.put(parameterUsage.typeParameterDescriptor,
                               parameterUsage.howTheTypeParameterIsUsed.superpose(howTheTypeIsUsedBefore));
                return Unit.INSTANCE;
            };
            processAllTypeParameters(withParameters, Variance.INVARIANT, processor, parameters::containsKey);
            processAllTypeParameters(expected, Variance.INVARIANT, processor, parameters::containsKey);
            ConstraintSystem.Builder constraintSystem = new ConstraintSystemBuilderImpl();
            TypeSubstitutor substitutor = constraintSystem.registerTypeVariables(CallHandle.NONE.INSTANCE, parameters.keySet(), false);
            constraintSystem.addSubtypeConstraint(withParameters, substitutor.substitute(expected, Variance.INVARIANT), SPECIAL.position());

            return constraintSystem.build().getStatus().isSuccessful();
        }

        private static void processAllTypeParameters(
                KotlinType type,
                Variance howThisTypeIsUsed,
                Function1<TypeParameterUsage, Unit> result,
                Function1<TypeParameterDescriptor, Boolean> containsParameter
        ) {
            ClassifierDescriptor descriptor = type.getConstructor().getDeclarationDescriptor();
            if (descriptor instanceof TypeParameterDescriptor) {
                if (containsParameter.invoke((TypeParameterDescriptor) descriptor)) return;

                result.invoke(new TypeParameterUsage((TypeParameterDescriptor) descriptor, howThisTypeIsUsed));

                for (KotlinType superType : type.getConstructor().getSupertypes()) {
                    processAllTypeParameters(superType, howThisTypeIsUsed, result, containsParameter);
                }
            }
            for (TypeProjection projection : type.getArguments()) {
                if (projection.isStarProjection()) continue;
                processAllTypeParameters(projection.getType(), projection.getProjectionKind(), result, containsParameter);
            }
        }
    }
}
