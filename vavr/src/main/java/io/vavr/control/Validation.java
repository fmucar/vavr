/*  __    __  __  __    __  ___
 * \  \  /  /    \  \  /  /  __/
 *  \  \/  /  /\  \  \/  /  /
 *   \____/__/  \__\____/__/
 *
 * Copyright 2014-2018 Vavr, http://vavr.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.vavr.control;

import io.vavr.*;
import io.vavr.collection.Iterator;
import io.vavr.collection.List;
import io.vavr.collection.Seq;

import java.io.Serializable;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * An implementation similar to scalaz's <a href="http://eed3si9n.com/learning-scalaz/Validation.html">Validation</a> control.
 * <p>
 * The Validation type is different from a Monad type, it is an applicative
 * functor. Whereas a Monad will short circuit after the first errors, the
 * applicative functor will continue on, accumulating ALL errors. This is
 * especially helpful in cases such as validation, where you want to know
 * all the validation errors that have occurred, not just the first one.
 * <p>
 * <strong>Validation construction:</strong>
 *
 * <pre>{@code
 * // = Valid(5)
 * Validation<String, Integer> valid = Validation.valid(5);
 *
 * // = Invalid(List("error1", "error2"))
 * Validation<String, Integer> invalid = Validation.invalid("error1", "error2");
 * }</pre>
 *
 * <strong>Validation combination:</strong>
 *
 * <pre>{@code
 * Validation<String, String> valid1 = Validation.valid("John");
 * Validation<String, Integer> valid2 = Validation.valid(5);
 * Validation<String, Option<String>> valid3 = Validation.valid(Option.of("123 Fake St."));
 * Function3<String, Integer, Option<String>, Person> f = ...;
 *
 * Validation<String, String> result = valid1.combine(valid2).ap((name,age) -> "Name: " + name + " Age: " + age);
 * Validation<String, Person> result2 = valid1.combine(valid2).combine(valid3).ap(f);
 * }</pre>
 *
 * <strong>Another form of combining validations:</strong>
 *
 * <pre>{@code
 * Validation<String, Person> result3 = Validation.combine(valid1, valid2, valid3).ap(f);
 * }</pre>
 *
 * <hr>
 *
 * <strong>Background:</strong>
 *
 * Validation is an <em>Applicative</em> that satisfies the following laws (beside the Functor laws):
 *
 * <ul>
 * <li>identity: {@code validation.ap(lift(v -> v)) = validation}</li>
 * <li>homomorphism: {@code valid(v).ap(lift(f)) = valid(f.apply(v))}</li>
 * <li>interchange: {@code valid(v).ap(lift(f)) = lift(f).ap(lift(g -> g.apply(v)))}</li>
 * </ul>
 *
 * See also <a href="http://www.staff.city.ac.uk/~ross/papers/Applicative.pdf">Applicative programming with effects</a>
 * by Conor McBride and Ross Patterson (2008).
 *
 * @param <E> value type in the case of invalid
 * @param <T> value type in the case of valid
 * @author Eric Nelson
 * @see <a href="https://github.com/scalaz/scalaz/blob/series/7.3.x/core/src/main/scala/scalaz/Validation.scala">Validation</a>
 */
public interface Validation<E, T> extends Value<T>, Serializable {

    long serialVersionUID = 1L;

    /**
     * Creates a {@link Valid} that contains the given {@code value}.
     *
     * @param <E>   type of the error
     * @param <T>   type of the given {@code value}
     * @param value A value
     * @return {@code Valid(value)}
     */
    static <E, T> Validation<E, T> valid(T value) {
        return new Valid<>(value);
    }

    /**
     * Lifts a given function {@code f} to a {@code Validation}.
     * <p>
     * This method is syntactic sugar because {@link Validation#valid(Object)} does not accept lambdas.
     *
     * @param f   a lambda expression
     * @param <E> type of the error
     * @param <T> input type of the given function {@code f}
     * @param <U> output type of the given function {@code f}
     * @return a new {@code Valid} instance containing the given function {@code f}
     */
    @SuppressWarnings("unchecked")
    static <E, T, U> Validation<E, Function<T, U>> lift(Function<? super T, ? extends U> f) {
        return new Valid<>((Function<T, U>) f);
    }

    /**
     * Creates an {@link Invalid} that contains the given {@code error}.
     *
     * @param <E>   type of the given {@code error}
     * @param <T>   type of the value
     * @param error a validation error that is encapsulated by the invalid state
     * @return a new {@link Validation.Invalid} instance that contains the given {@code error}
     * @throws NullPointerException if {@code error} is null
     */
    static <E, T> Validation<E, T> invalid(E error) {
        Objects.requireNonNull(error, "error is null");
        return new Invalid<>(List.of(error));
    }

    /**
     * Creates an {@link Invalid} that contains the given {@code errors}.
     *
     * @param <E>    type of the given {@code errors}
     * @param <T>    type of the value
     * @param errors validation errors that are encapsulated by the invalid state. Empty errors are allowed (because of {@link #filter(Predicate)}) but not recommended.
     * @return a new {@link Validation.Invalid} instance that contains the given {@code errors}
     * @throws NullPointerException if {@code errors} is null
     */
    @SuppressWarnings("varargs")
    @SafeVarargs
    static <E, T> Validation<E, T> invalid(E... errors) {
        Objects.requireNonNull(errors, "errors is null");
        return invalidAll(List.of(errors));
    }

    /**
     * Creates an {@link Invalid} that contains the given {@code errors}.
     *
     * @param <E>    component type of the given {@code errors}
     * @param <T>    type of the value
     * @param errors validation errors that are encapsulated by the invalid state. Empty errors are allowed (because of {@link #filter(Predicate)}) but not recommended.
     * @return a new {@link Validation.Invalid} instance that contains the given {@code errors}
     * @throws NullPointerException if {@code errors} is null
     */
    @SuppressWarnings("unchecked")
    static <E, T> Validation<E, T> invalidAll(Iterable<? extends E> errors) {
        Objects.requireNonNull(errors, "errors is null");
        final Seq<E> errorSeq = (errors instanceof Seq) ? (Seq<E>) errors : List.ofAll(errors);
        return new Invalid<>(errorSeq);
    }

    /**
     * Creates a {@code Validation} of an {@code Either}.
     *
     * @param either An {@code Either}
     * @param <E>    error type
     * @param <T>    value type
     * @return A {@code Valid(either.get())} if either is a Right, otherwise {@code Invalid(either.getLeft())}.
     * @throws NullPointerException if either is null
     */
    static <E, T> Validation<E, T> fromEither(Either<E, T> either) {
        Objects.requireNonNull(either, "either is null");
        return either.isRight() ? valid(either.get()) : invalid(either.getLeft());
    }

    /**
     * Creates a {@code Validation} of an {@code Try}.
     *
     * @param t   A {@code Try}
     * @param <T> type of the valid value
     * @return A {@code Valid(t.get())} if t is a Success, otherwise {@code Invalid(t.getCause())}.
     * @throws NullPointerException if {@code t} is null
     */
    static <T> Validation<Throwable, T> fromTry(Try<? extends T> t) {
        Objects.requireNonNull(t, "t is null");
        return t.isSuccess() ? valid(t.get()) : invalid(t.getCause());
    }

    /**
     * Reduces many {@code Validation} instances into a single {@code Validation} by transforming an
     * {@code Iterable<Validation<? extends T>>} into a {@code Validation<Seq<T>>}.
     *
     * @param <E>    value type in the case of invalid
     * @param <T>    value type in the case of valid
     * @param values An iterable of Validation instances.
     * @return A valid Validation of a sequence of values if all Validation instances are valid
     * or an invalid Validation containing an accumulated List of errors.
     * @throws NullPointerException if values is null
     */
    static <E, T> Validation<E, Seq<T>> sequence(Iterable<? extends Validation<? extends E, ? extends T>> values) {
        Objects.requireNonNull(values, "values is null");
        List<E> errors = List.empty();
        List<T> list = List.empty();
        for (Validation<? extends E, ? extends T> value : values) {
            if (value.isInvalid()) {
                errors = errors.prependAll(value.getErrors().reverse());
            } else if (errors.isEmpty()) {
                list = list.prepend(value.get());
            }
        }
        return errors.isEmpty() ? valid(list.reverse()) : invalidAll(errors.reverse());
    }

    /**
     * Maps the values of an iterable to a sequence of mapped values into a single {@code Validation} by
     * transforming an {@code Iterable<? extends T>} into a {@code Validation<Seq<U>>}.
     * <p>
     *
     * @param values   An {@code Iterable} of values.
     * @param mapper   A mapper of values to Validations
     * @param <T>      The type of the given values.
     * @param <E>      The mapped error value type.
     * @param <U>      The mapped valid value type.
     * @return A {@code Validation} of a {@link Seq} of results.
     * @throws NullPointerException if values or f is null.
     */
    static <E, T, U> Validation<E, Seq<U>> traverse(Iterable<? extends T> values, Function<? super T, ? extends Validation<? extends E, ? extends U>> mapper) {
        Objects.requireNonNull(values, "values is null");
        Objects.requireNonNull(mapper, "mapper is null");
        return sequence(Iterator.ofAll(values).map(mapper));
    }

    /**
     * Narrows a widened {@code Validation<? extends E, ? extends T>} to {@code Validation<E, T>}
     * by performing a type-safe cast. This is eligible because immutable/read-only
     * collections are covariant.
     *
     * @param validation A {@code Validation}.
     * @param <E>        type of error
     * @param <T>        type of valid value
     * @return the given {@code validation} instance as narrowed type {@code Validation<E, T>}.
     */
    @SuppressWarnings("unchecked")
    static <E, T> Validation<E, T> narrow(Validation<? extends E, ? extends T> validation) {
        return (Validation<E, T>) validation;
    }

    /**
     * Combines two {@code Validation}s into a {@link Builder2}.
     *
     * @param <E>         type of error
     * @param <T1>        type of first valid value
     * @param <T2>        type of second valid value
     * @param validation1 first validation
     * @param validation2 second validation
     * @return an instance of Builder&lt;E,T1,T2&gt;
     * @throws NullPointerException if validation1 or validation2 is null
     */
    static <E, T1, T2> Builder2<E, T1, T2> combine(Validation<E, T1> validation1, Validation<E, T2> validation2) {
        Objects.requireNonNull(validation1, "validation1 is null");
        Objects.requireNonNull(validation2, "validation2 is null");
        return new Builder2<>(validation1, validation2);
    }

    /**
     * Combines three {@code Validation}s into a {@link Builder3}.
     *
     * @param <E>         type of error
     * @param <T1>        type of first valid value
     * @param <T2>        type of second valid value
     * @param <T3>        type of third valid value
     * @param validation1 first validation
     * @param validation2 second validation
     * @param validation3 third validation
     * @return an instance of Builder3&lt;E,T1,T2,T3&gt;
     * @throws NullPointerException if validation1, validation2 or validation3 is null
     */
    static <E, T1, T2, T3> Builder3<E, T1, T2, T3> combine(Validation<E, T1> validation1, Validation<E, T2> validation2, Validation<E, T3> validation3) {
        Objects.requireNonNull(validation1, "validation1 is null");
        Objects.requireNonNull(validation2, "validation2 is null");
        Objects.requireNonNull(validation3, "validation3 is null");
        return new Builder3<>(validation1, validation2, validation3);
    }

    /**
     * Combines four {@code Validation}s into a {@link Builder4}.
     *
     * @param <E>         type of error
     * @param <T1>        type of first valid value
     * @param <T2>        type of second valid value
     * @param <T3>        type of third valid value
     * @param <T4>        type of fourth valid value
     * @param validation1 first validation
     * @param validation2 second validation
     * @param validation3 third validation
     * @param validation4 fourth validation
     * @return an instance of Builder3&lt;E,T1,T2,T3,T4&gt;
     * @throws NullPointerException if validation1, validation2, validation3 or validation4 is null
     */
    static <E, T1, T2, T3, T4> Builder4<E, T1, T2, T3, T4> combine(Validation<E, T1> validation1, Validation<E, T2> validation2, Validation<E, T3> validation3, Validation<E, T4> validation4) {
        Objects.requireNonNull(validation1, "validation1 is null");
        Objects.requireNonNull(validation2, "validation2 is null");
        Objects.requireNonNull(validation3, "validation3 is null");
        Objects.requireNonNull(validation4, "validation4 is null");
        return new Builder4<>(validation1, validation2, validation3, validation4);
    }

    /**
     * Combines five {@code Validation}s into a {@link Builder5}.
     *
     * @param <E>         type of error
     * @param <T1>        type of first valid value
     * @param <T2>        type of second valid value
     * @param <T3>        type of third valid value
     * @param <T4>        type of fourth valid value
     * @param <T5>        type of fifth valid value
     * @param validation1 first validation
     * @param validation2 second validation
     * @param validation3 third validation
     * @param validation4 fourth validation
     * @param validation5 fifth validation
     * @return an instance of Builder3&lt;E,T1,T2,T3,T4,T5&gt;
     * @throws NullPointerException if validation1, validation2, validation3, validation4 or validation5 is null
     */
    static <E, T1, T2, T3, T4, T5> Builder5<E, T1, T2, T3, T4, T5> combine(Validation<E, T1> validation1, Validation<E, T2> validation2, Validation<E, T3> validation3, Validation<E, T4> validation4, Validation<E, T5> validation5) {
        Objects.requireNonNull(validation1, "validation1 is null");
        Objects.requireNonNull(validation2, "validation2 is null");
        Objects.requireNonNull(validation3, "validation3 is null");
        Objects.requireNonNull(validation4, "validation4 is null");
        Objects.requireNonNull(validation5, "validation5 is null");
        return new Builder5<>(validation1, validation2, validation3, validation4, validation5);
    }

    /**
     * Combines six {@code Validation}s into a {@link Builder6}.
     *
     * @param <E>         type of error
     * @param <T1>        type of first valid value
     * @param <T2>        type of second valid value
     * @param <T3>        type of third valid value
     * @param <T4>        type of fourth valid value
     * @param <T5>        type of fifth valid value
     * @param <T6>        type of sixth valid value
     * @param validation1 first validation
     * @param validation2 second validation
     * @param validation3 third validation
     * @param validation4 fourth validation
     * @param validation5 fifth validation
     * @param validation6 sixth validation
     * @return an instance of Builder3&lt;E,T1,T2,T3,T4,T5,T6&gt;
     * @throws NullPointerException if validation1, validation2, validation3, validation4, validation5 or validation6 is null
     */
    static <E, T1, T2, T3, T4, T5, T6> Builder6<E, T1, T2, T3, T4, T5, T6> combine(Validation<E, T1> validation1, Validation<E, T2> validation2, Validation<E, T3> validation3, Validation<E, T4> validation4, Validation<E, T5> validation5, Validation<E, T6> validation6) {
        Objects.requireNonNull(validation1, "validation1 is null");
        Objects.requireNonNull(validation2, "validation2 is null");
        Objects.requireNonNull(validation3, "validation3 is null");
        Objects.requireNonNull(validation4, "validation4 is null");
        Objects.requireNonNull(validation5, "validation5 is null");
        Objects.requireNonNull(validation6, "validation6 is null");
        return new Builder6<>(validation1, validation2, validation3, validation4, validation5, validation6);
    }

    /**
     * Combines seven {@code Validation}s into a {@link Builder7}.
     *
     * @param <E>         type of error
     * @param <T1>        type of first valid value
     * @param <T2>        type of second valid value
     * @param <T3>        type of third valid value
     * @param <T4>        type of fourth valid value
     * @param <T5>        type of fifth valid value
     * @param <T6>        type of sixth valid value
     * @param <T7>        type of seventh valid value
     * @param validation1 first validation
     * @param validation2 second validation
     * @param validation3 third validation
     * @param validation4 fourth validation
     * @param validation5 fifth validation
     * @param validation6 sixth validation
     * @param validation7 seventh validation
     * @return an instance of Builder3&lt;E,T1,T2,T3,T4,T5,T6,T7&gt;
     * @throws NullPointerException if validation1, validation2, validation3, validation4, validation5, validation6 or validation7 is null
     */
    static <E, T1, T2, T3, T4, T5, T6, T7> Builder7<E, T1, T2, T3, T4, T5, T6, T7> combine(Validation<E, T1> validation1, Validation<E, T2> validation2, Validation<E, T3> validation3, Validation<E, T4> validation4, Validation<E, T5> validation5, Validation<E, T6> validation6, Validation<E, T7> validation7) {
        Objects.requireNonNull(validation1, "validation1 is null");
        Objects.requireNonNull(validation2, "validation2 is null");
        Objects.requireNonNull(validation3, "validation3 is null");
        Objects.requireNonNull(validation4, "validation4 is null");
        Objects.requireNonNull(validation5, "validation5 is null");
        Objects.requireNonNull(validation6, "validation6 is null");
        Objects.requireNonNull(validation7, "validation7 is null");
        return new Builder7<>(validation1, validation2, validation3, validation4, validation5, validation6, validation7);
    }

    /**
     * Combines eight {@code Validation}s into a {@link Builder8}.
     *
     * @param <E>         type of error
     * @param <T1>        type of first valid value
     * @param <T2>        type of second valid value
     * @param <T3>        type of third valid value
     * @param <T4>        type of fourth valid value
     * @param <T5>        type of fifth valid value
     * @param <T6>        type of sixth valid value
     * @param <T7>        type of seventh valid value
     * @param <T8>        type of eighth valid value
     * @param validation1 first validation
     * @param validation2 second validation
     * @param validation3 third validation
     * @param validation4 fourth validation
     * @param validation5 fifth validation
     * @param validation6 sixth validation
     * @param validation7 seventh validation
     * @param validation8 eighth validation
     * @return an instance of Builder3&lt;E,T1,T2,T3,T4,T5,T6,T7,T8&gt;
     * @throws NullPointerException if validation1, validation2, validation3, validation4, validation5, validation6, validation7 or validation8 is null
     */
    static <E, T1, T2, T3, T4, T5, T6, T7, T8> Builder8<E, T1, T2, T3, T4, T5, T6, T7, T8> combine(Validation<E, T1> validation1, Validation<E, T2> validation2, Validation<E, T3> validation3, Validation<E, T4> validation4, Validation<E, T5> validation5, Validation<E, T6> validation6, Validation<E, T7> validation7, Validation<E, T8> validation8) {
        Objects.requireNonNull(validation1, "validation1 is null");
        Objects.requireNonNull(validation2, "validation2 is null");
        Objects.requireNonNull(validation3, "validation3 is null");
        Objects.requireNonNull(validation4, "validation4 is null");
        Objects.requireNonNull(validation5, "validation5 is null");
        Objects.requireNonNull(validation6, "validation6 is null");
        Objects.requireNonNull(validation7, "validation7 is null");
        Objects.requireNonNull(validation8, "validation8 is null");
        return new Builder8<>(validation1, validation2, validation3, validation4, validation5, validation6, validation7, validation8);
    }

    /**
     * Applies a given {@code Validation} that encapsulates a function to this {@code Validation}'s value or combines both errors.
     *
     * @param validation a function that transforms this value (on the 'sunny path')
     * @param <U>        the new value type
     * @return a new {@code Validation} that contains a transformed value or combined errors.
     */
    @SuppressWarnings("unchecked")
    default <U> Validation<E, U> ap(Validation<E, ? extends Function<? super T, ? extends U>> validation) {
        Objects.requireNonNull(validation, "validation is null");
        if (isValid()) {
            return validation.map(f -> f.apply(get()));
        } else if (validation.isValid()) {
            return (Validation<E, U>) this;
        } else {
            return invalidAll(getErrors().prependAll(validation.getErrors()));
        }
    }

    /**
     * Check whether this is of type {@code Valid}
     *
     * @return true if is a Valid, false if is an Invalid
     */
    boolean isValid();

    /**
     * Check whether this is of type {@code Invalid}
     *
     * @return true if is an Invalid, false if is a Valid
     */
    boolean isInvalid();

    /**
     * Returns this {@code Validation} if it is valid, otherwise return the alternative.
     *
     * @param other An alternative {@code Validation}
     * @return this {@code Validation} if it is valid, otherwise return the alternative.
     */
    @SuppressWarnings("unchecked")
    default Validation<E, T> orElse(Validation<? extends E, ? extends T> other) {
        Objects.requireNonNull(other, "other is null");
        return isValid() ? this : (Validation<E, T>) other;
    }

    /**
     * Returns this {@code Validation} if it is valid, otherwise return the result of evaluating supplier.
     *
     * @param supplier An alternative {@code Validation} supplier
     * @return this {@code Validation} if it is valid, otherwise return the result of evaluating supplier.
     */
    @SuppressWarnings("unchecked")
    default Validation<E, T> orElse(Supplier<Validation<? extends E, ? extends T>> supplier) {
        Objects.requireNonNull(supplier, "supplier is null");
        return isValid() ? this : (Validation<E, T>) supplier.get();
    }

    @Override
    default boolean isEmpty() {
        return isInvalid();
    }

    /**
     * Gets the value of this {@code Validation} if is a {@code Valid} or throws if this is an {@code Invalid}.
     *
     * @return The value of this {@code Validation}
     * @throws NoSuchElementException if this is an {@code Invalid}
     */
    @Override
    T get();

    /**
     * Gets the value if it is a Valid or an value calculated from the errors
     *
     * @param other a function which converts an error to an alternative value
     * @return the value, if the underlying Validation is a Valid, or else the alternative value
     * provided by {@code other} by applying the errors.
     */
    default T getOrElseGet(Function<? super Seq<? super E>, ? extends T> other) {
        Objects.requireNonNull(other, "other is null");
        if (isValid()) {
            return get();
        } else {
            return other.apply(getErrors());
        }
    }

    /**
     * Gets the errors of this Validation if is an Invalid or throws if this is a Valid
     *
     * @return The errors of this Invalid
     * @throws RuntimeException if this is a Valid
     */
    Seq<E> getErrors();

    /**
     * Returns this as {@code Either}.
     *
     * @return {@code Either.right(get())} if this is valid, otherwise {@code Either.left(getErrors())}.
     */
    default Either<Seq<E>, T> toEither() {
        return isValid() ? Either.right(get()) : Either.left(getErrors());
    }

    @Override
    boolean equals(Object o);

    @Override
    int hashCode();

    @Override
    String toString();

    /**
     * Performs the given action for the value contained in {@code Valid}, or do nothing
     * if this is an Invalid.
     *
     * @param action the action to be performed on the contained value
     * @throws NullPointerException if action is null
     */
    @Override
    default void forEach(Consumer<? super T> action) {
        Objects.requireNonNull(action, "action is null");
        if (isValid()) {
            action.accept(get());
        }
    }

    /**
     * Performs the action in {@code fInvalid} on {@code errors} if this is an {@code Invalid},
     * or {@code fValid} on {@code value} if this is a {@code Valid}.
     * Returns an object of type U.
     *
     * <p>
     * <code>
     * For example:<br>
     * Validation&lt;List&lt;String&gt;,String&gt; valid = ...;<br>
     * Integer i = valid.fold(List::length, String::length);
     * </code>
     * </p>
     *
     * @param <U>      the fold result type
     * @param fInvalid the invalid fold operation
     * @param fValid   the valid fold operation
     * @return an instance of type U
     * @throws NullPointerException if fInvalid or fValid is null
     */
    default <U> U fold(Function<? super Seq<? super E>, ? extends U> fInvalid, Function<? super T, ? extends U> fValid) {
        Objects.requireNonNull(fInvalid, "fInvalid is null");
        Objects.requireNonNull(fValid, "fValid is null");
        if (isValid()) {
            return fValid.apply(get());
        } else {
            return fInvalid.apply(getErrors());
        }
    }

    /**
     * Flip the valid/invalid values for this Validation.
     * <p>
     * If this is a {@code Valid<E, T>}, {@code swap} returns an {@code Invalid<T, Seq<E>>}.
     * <p>
     * If this is an {@code Invalid<E, T>}, {@code swap} returns a {@code Valid<T, Seq<E>>}.
     * <p>
     * Please note that {@code validation.swap().swap()} isn't the identity {@code validation}.
     *
     * @return a flipped instance of {@code Validation}
     */
    default Validation<T, Seq<E>> swap() {
        if (isValid()) {
            return invalid(get());
        } else {
            return valid(getErrors());
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    default <U> Validation<E, U> map(Function<? super T, ? extends U> f) {
        Objects.requireNonNull(f, "f is null");
        if (isValid()) {
            return valid(f.apply(get()));
        } else {
            return (Validation<E, U>) this;
        }
    }

    /**
     * Maps the errors if this {@code Validation} is an {@code Invalid}, otherwise does nothing.
     * <p>
     * <strong>Hint</strong>: if a transformation of errors is needed use {@code getErrors().map(f)} instead.
     * They can be wrapped in an {@code Invalid} again using {@link #invalidAll(Iterable)}.
     *
     * @param <U> type of the errors resulting from the mapping
     * @param f   a function that maps errors
     * @return an instance of {@code Validation<U, T>}
     * @throws NullPointerException if the given function {@code f} is null
     */
    @SuppressWarnings("unchecked")
    default <U> Validation<U, T> mapErrors(Function<? super E, ? extends U> f) {
        Objects.requireNonNull(f, "f is null");
        if (isValid()) {
            return (Validation<U, T>) this;
        } else {
            return invalidAll(getErrors().map(f));
        }
    }

    /**
     * Whereas map only performs a mapping on a valid Validation, and mapError performs a mapping on an invalid
     * Validation, bimap allows you to provide mapping actions for both, and will give you the result based
     * on what type of Validation this is. Without this, you would have to do something like:
     *
     * validation.map(...).mapError(...);
     *
     * @param <E2>        type of the mapping result if this is an invalid
     * @param <T2>        type of the mapping result if this is a valid
     * @param errorMapper the invalid mapping operation
     * @param valueMapper the valid mapping operation
     * @return an instance of Validation&lt;U,R&gt;
     * @throws NullPointerException if invalidMapper or validMapper is null
     */
    default <E2, T2> Validation<E2, T2> bimap(
            Function<? super E, ? extends E2> errorMapper,
            Function<? super T, ? extends T2> valueMapper) {
        Objects.requireNonNull(errorMapper, "errorMapper is null");
        Objects.requireNonNull(valueMapper, "valueMapper is null");
        if (isValid()) {
            return valid(valueMapper.apply(get()));
        } else {
            return invalidAll(getErrors().map(errorMapper));
        }
    }

    /**
     * Combines two {@code Validation}s to form a {@link Builder2}, which can then be used to perform further
     * combines, or apply a function to it in order to transform the {@link Builder2} into a {@code Validation}.
     *
     * @param <U>        type of the value contained in validation
     * @param validation the validation object to combine this with
     * @return an instance of Builder
     */
    default <U> Builder2<E, T, U> combine(Validation<E, U> validation) {
        return new Builder2<>(this, validation);
    }

    // -- Implementation of Value

    /**
     * Tests the value using the given {@code predicate} if this is valid.
     * Returns this instance, if this is valid and the value makes it through the filter.
     * <p>
     * If a value does not make it through the filter, an {@link Invalid} instance is returned, having an empty error list.
     *
     * <pre>{@code
     * // = Valid(1)
     * Validation.valid(1).filter(i -> i == 1)
     *
     * // = Invalid(List())
     * Validation.valid(1).filter(i -> i == 2)
     *
     * // = Invalid(List("err1", "err2"))
     * Validation.invalid("err1", "err2").filter(o -> true)
     * }</pre>
     *
     * @param predicate a filter function
     * @return a {@code Validation} instance
     */
    default Validation<E, T> filter(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate is null");
        return isInvalid() || predicate.test(get()) ? this : invalid();
    }

    /**
     * Applies a given function {@code f} to the <strong>error sequence</strong> of this {@code Validation}
     * if this is an {@code Invalid}. Otherwise does nothing if this is a {@code Valid}.
     *
     * @param predicate a filtering function that tests errors
     * @return an instance of {@code Validation<E, T>}
     * @throws NullPointerException if the give {@code predicate} is null
     */
    @SuppressWarnings("unchecked")
    default Validation<E, T> filterErrors(Predicate<? super E> predicate) {
        Objects.requireNonNull(predicate, "predicate is null");
        if (isValid()) {
            return this;
        } else {
            return invalidAll(getErrors().filter(predicate));
        }
    }

    @SuppressWarnings("unchecked")
    default <U> Validation<E, U> flatMap(Function<? super T, ? extends Validation<E, ? extends U>> mapper) {
        Objects.requireNonNull(mapper, "mapper is null");
        return isValid() ? (Validation<E, U>) mapper.apply(get()) : (Validation<E, U>) this;
    }

    /**
     * FlatMaps the errors if this {@code Validation} is an {@code Invalid}, otherwise does nothing.
     *
     * @param <U> type of the errors resulting from the mapping
     * @param f   a function that maps errors to sequences
     * @return an instance of {@code Validation<U, T>}
     * @throws NullPointerException if the given function {@code f} is null
     */
    @SuppressWarnings("unchecked")
    default <U> Validation<U, T> flatMapErrors(Function<? super E, ? extends Iterable<? extends U>> f) {
        Objects.requireNonNull(f, "f is null");
        if (isValid()) {
            return (Validation<U, T>) this;
        } else {
            return invalidAll(getErrors().flatMap(f));
        }
    }

    @Override
    default Validation<E, T> peek(Consumer<? super T> action) {
        if (isValid()) {
            action.accept(get());
        }
        return this;
    }

    /**
     * Consumes the errors if this is an Invalid.
     *
     * @param action The action that will be performed on the errors.
     * @return this instance
     */
    default Validation<E, T> peekInvalid(Consumer<? super Seq<E>> action) {
        if (isInvalid()) {
            action.accept(getErrors());
        }
        return this;
    }

    /**
     * A {@code Validation}'s value is computed synchronously.
     *
     * @return false
     */
    @Override
    default boolean isAsync() {
        return false;
    }

    /**
     * A {@code Validation}'s value is computed eagerly.
     *
     * @return false
     */
    @Override
    default boolean isLazy() {
        return false;
    }

    @Override
    default boolean isSingleValued() {
        return true;
    }

    @Override
    default Iterator<T> iterator() {
        return isValid() ? Iterator.of(get()) : Iterator.empty();
    }

    /**
     * A valid Validation
     *
     * @param <E> type of the errors of this Validation
     * @param <T> type of the value of this Validation
     */
    final class Valid<E, T> implements Validation<E, T>, Serializable {

        private static final long serialVersionUID = 1L;

        private final T value;

        /**
         * Construct a {@code Valid}
         *
         * @param value The value of this success
         */
        private Valid(T value) {
            this.value = value;
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public boolean isInvalid() {
            return false;
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public Seq<E> getErrors() throws RuntimeException {
            throw new NoSuchElementException("errors of 'valid' Validation");
        }

        @Override
        public boolean equals(Object obj) {
            return (obj == this) || (obj instanceof Valid && Objects.equals(value, ((Valid<?, ?>) obj).value));
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }

        @Override
        public String stringPrefix() {
            return "Valid";
        }

        @Override
        public String toString() {
            return stringPrefix() + "(" + value + ")";
        }

    }

    /**
     * An invalid Validation
     *
     * @param <E> type of the errors of this Validation
     * @param <T> type of the value of this Validation
     */
    final class Invalid<E, T> implements Validation<E, T>, Serializable {

        private static final long serialVersionUID = 1L;

        private final Seq<E> errors;

        private Invalid(Seq<E> errors) {
            this.errors = errors;
        }

        @Override
        public boolean isValid() {
            return false;
        }

        @Override
        public boolean isInvalid() {
            return true;
        }

        @Override
        public T get() throws NoSuchElementException {
            throw new NoSuchElementException("get of 'invalid' Validation");
        }

        @Override
        public Seq<E> getErrors() {
            return errors;
        }

        @Override
        public boolean equals(Object obj) {
            return (obj == this) || (obj instanceof Invalid && Objects.equals(errors, ((Invalid<?, ?>) obj).errors));
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(errors);
        }

        @Override
        public String stringPrefix() {
            return "Invalid";
        }

        @Override
        public String toString() {
            return stringPrefix() + "(" + errors + ")";
        }

    }

    final class Builder2<E, T1, T2> {

        private final Validation<E, T1> v1;
        private final Validation<E, T2> v2;

        private Builder2(Validation<E, T1> v1, Validation<E, T2> v2) {
            this.v1 = v1;
            this.v2 = v2;
        }

        public <R> Validation<E, R> ap(Function2<T1, T2, R> f) {
            return v2.ap(v1.ap(valid(f.curried())));
        }

        public <T3> Builder3<E, T1, T2, T3> combine(Validation<E, T3> v3) {
            return new Builder3<>(v1, v2, v3);
        }

    }

    final class Builder3<E, T1, T2, T3> {

        private final Validation<E, T1> v1;
        private final Validation<E, T2> v2;
        private final Validation<E, T3> v3;

        private Builder3(Validation<E, T1> v1, Validation<E, T2> v2, Validation<E, T3> v3) {
            this.v1 = v1;
            this.v2 = v2;
            this.v3 = v3;
        }

        public <R> Validation<E, R> ap(Function3<T1, T2, T3, R> f) {
            return v3.ap(v2.ap(v1.ap(valid(f.curried()))));
        }

        public <T4> Builder4<E, T1, T2, T3, T4> combine(Validation<E, T4> v4) {
            return new Builder4<>(v1, v2, v3, v4);
        }

    }

    final class Builder4<E, T1, T2, T3, T4> {

        private final Validation<E, T1> v1;
        private final Validation<E, T2> v2;
        private final Validation<E, T3> v3;
        private final Validation<E, T4> v4;

        private Builder4(Validation<E, T1> v1, Validation<E, T2> v2, Validation<E, T3> v3, Validation<E, T4> v4) {
            this.v1 = v1;
            this.v2 = v2;
            this.v3 = v3;
            this.v4 = v4;
        }

        public <R> Validation<E, R> ap(Function4<T1, T2, T3, T4, R> f) {
            return v4.ap(v3.ap(v2.ap(v1.ap(valid(f.curried())))));
        }

        public <T5> Builder5<E, T1, T2, T3, T4, T5> combine(Validation<E, T5> v5) {
            return new Builder5<>(v1, v2, v3, v4, v5);
        }

    }

    final class Builder5<E, T1, T2, T3, T4, T5> {

        private final Validation<E, T1> v1;
        private final Validation<E, T2> v2;
        private final Validation<E, T3> v3;
        private final Validation<E, T4> v4;
        private final Validation<E, T5> v5;

        private Builder5(Validation<E, T1> v1, Validation<E, T2> v2, Validation<E, T3> v3, Validation<E, T4> v4, Validation<E, T5> v5) {
            this.v1 = v1;
            this.v2 = v2;
            this.v3 = v3;
            this.v4 = v4;
            this.v5 = v5;
        }

        public <R> Validation<E, R> ap(Function5<T1, T2, T3, T4, T5, R> f) {
            return v5.ap(v4.ap(v3.ap(v2.ap(v1.ap(valid(f.curried()))))));
        }

        public <T6> Builder6<E, T1, T2, T3, T4, T5, T6> combine(Validation<E, T6> v6) {
            return new Builder6<>(v1, v2, v3, v4, v5, v6);
        }

    }

    final class Builder6<E, T1, T2, T3, T4, T5, T6> {

        private final Validation<E, T1> v1;
        private final Validation<E, T2> v2;
        private final Validation<E, T3> v3;
        private final Validation<E, T4> v4;
        private final Validation<E, T5> v5;
        private final Validation<E, T6> v6;

        private Builder6(Validation<E, T1> v1, Validation<E, T2> v2, Validation<E, T3> v3, Validation<E, T4> v4, Validation<E, T5> v5, Validation<E, T6> v6) {
            this.v1 = v1;
            this.v2 = v2;
            this.v3 = v3;
            this.v4 = v4;
            this.v5 = v5;
            this.v6 = v6;
        }

        public <R> Validation<E, R> ap(Function6<T1, T2, T3, T4, T5, T6, R> f) {
            return v6.ap(v5.ap(v4.ap(v3.ap(v2.ap(v1.ap(valid(f.curried())))))));
        }

        public <T7> Builder7<E, T1, T2, T3, T4, T5, T6, T7> combine(Validation<E, T7> v7) {
            return new Builder7<>(v1, v2, v3, v4, v5, v6, v7);
        }

    }

    final class Builder7<E, T1, T2, T3, T4, T5, T6, T7> {

        private final Validation<E, T1> v1;
        private final Validation<E, T2> v2;
        private final Validation<E, T3> v3;
        private final Validation<E, T4> v4;
        private final Validation<E, T5> v5;
        private final Validation<E, T6> v6;
        private final Validation<E, T7> v7;

        private Builder7(Validation<E, T1> v1, Validation<E, T2> v2, Validation<E, T3> v3, Validation<E, T4> v4, Validation<E, T5> v5, Validation<E, T6> v6, Validation<E, T7> v7) {
            this.v1 = v1;
            this.v2 = v2;
            this.v3 = v3;
            this.v4 = v4;
            this.v5 = v5;
            this.v6 = v6;
            this.v7 = v7;
        }

        public <R> Validation<E, R> ap(Function7<T1, T2, T3, T4, T5, T6, T7, R> f) {
            return v7.ap(v6.ap(v5.ap(v4.ap(v3.ap(v2.ap(v1.ap(valid(f.curried()))))))));
        }

        public <T8> Builder8<E, T1, T2, T3, T4, T5, T6, T7, T8> combine(Validation<E, T8> v8) {
            return new Builder8<>(v1, v2, v3, v4, v5, v6, v7, v8);
        }

    }

    final class Builder8<E, T1, T2, T3, T4, T5, T6, T7, T8> {

        private final Validation<E, T1> v1;
        private final Validation<E, T2> v2;
        private final Validation<E, T3> v3;
        private final Validation<E, T4> v4;
        private final Validation<E, T5> v5;
        private final Validation<E, T6> v6;
        private final Validation<E, T7> v7;
        private final Validation<E, T8> v8;

        private Builder8(Validation<E, T1> v1, Validation<E, T2> v2, Validation<E, T3> v3, Validation<E, T4> v4, Validation<E, T5> v5, Validation<E, T6> v6, Validation<E, T7> v7, Validation<E, T8> v8) {
            this.v1 = v1;
            this.v2 = v2;
            this.v3 = v3;
            this.v4 = v4;
            this.v5 = v5;
            this.v6 = v6;
            this.v7 = v7;
            this.v8 = v8;
        }

        public <R> Validation<E, R> ap(Function8<T1, T2, T3, T4, T5, T6, T7, T8, R> f) {
            return v8.ap(v7.ap(v6.ap(v5.ap(v4.ap(v3.ap(v2.ap(v1.ap(valid(f.curried())))))))));
        }
    }
}
