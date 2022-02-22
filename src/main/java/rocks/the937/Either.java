package rocks.the937;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An object representing one of either a left value type or a right value type, but not both.
 * This class is designed to treat Left values as some error or fail state, e.g., Exception, String describing
 * the failure, etc., and the Right type represents a successful return type.
 * It is possible for the value to be null.  Even in this case, the null's type depends on whether the Either
 * is Left or Right.
 * @param <L> the type of value contained in a Left either
 * @param <R> the type of value contained in a Right either
 */
public abstract class Either<L, R> {

    /**
     * A summation over Either objects, generated using the Either.collector() method.  All Left Either values will be
     * collected into the lefts list and all Right Either values will be collected into the rights list.
     * Since the lefts are considered to be error cases, the folding methods are designed to drop any right values
     * if left values are present.
     * This object is not related to {@link java.util.Collections}; this is simply the most appropriate term.
     */
    public static final class EitherCollection<LL, RR> {
        private final List<LL> lefts;
        private final List<RR> rights;
        private EitherCollection(final List<LL> _lefts, final List<RR> _rights) {
            lefts = Collections.unmodifiableList(_lefts);
            rights = Collections.unmodifiableList(_rights);
        }
        /** return whether this collection contains any left types or not */
        public boolean hasLefts() { return !lefts.isEmpty(); }
        /** get the possibly empty list of left types */
        public List<LL> getLefts() { return lefts; }
        /** get the possibly empty list of right types */
        public List<RR> getRights() { return rights; }

        /**
         * Convert this EitherCollection into a Left Either where the value is the result of calling the supplied folding
         * function on the list of left values, or if there are no left values, it becomes a Right Either containing
         * the list of Right values.  Note that the right values are lost if there are any left values.
         * @param fnLeft the function used to fold the left values
         * @param <T> the type to fold the left values into
         * @return Either the result of folding the left values, or if there are none, the unmodified right values
         */
        public <T> Either<T, List<RR>> foldLeft(final Function<List<LL>, T> fnLeft) {
            if (hasLefts()) return Either.left(fnLeft.apply(lefts));
            return Either.right(rights);
        }

        /**
         * Convert this EitherCollection into an Either by mapping the right values only if no lefts are present.
         * If this EitherCollection contains lefts, the returned Either will be a Left Either containing the lefts.
         * Otherwise, the rights will be folded into an element of type T by calling the supplied function and a
         * Right Either will be returned.
         * Note that the right values are lost if there are any left values.
         * @param fnRight the function to be applied to the rights if no lefts are present
         * @param <T> the type that the rights will be folded into
         * @return a left either containing the list of lefts if any are present, or a right either of type T constructed
         * by folding the rights using the supplied function
         */
        public <T> Either<List<LL>, T> foldRight(final Function<List<RR>, T> fnRight) {
            if (hasLefts()) return Either.left(lefts);
            return Either.right(fnRight.apply(rights));
        }

        /**
         * Collapse this collection into a single type.  If any left types exist, this function calls the supplied
         * fnLeft and returns a Left Either wrapping fnLeft's result.
         * Otherwise, this function calls fnRight and returns a Right Either wrapping fnRight's result.
         * Note that the right values are lost if there are any left values.
         * @param fnLeft the function to apply to the lefts, if there are any
         * @param fnRight the function to apply to the rights, if there are no lefts
         * @param <T> the type that both lefts and rights can be mapped to
         * @return the lefts folded into an object of type T, if any exist, otherwise the rights folded into an object of type T
         */
        public <T> T fold(final Function<List<LL>, T> fnLeft, final Function<List<RR>, T> fnRight) {
            if (hasLefts()) return fnLeft.apply(lefts);
            return fnRight.apply(rights);
        }
    }

    /**
     * An implementation of java's Collector that can be used with a stream of Either objects to collect them into
     * a EitherCollection object.
     * <code>
     *     System.out.println(Stream.<Either<Exception, Object>>generate(() -> {
     *         if (rand.nextBoolean()) return Either.left(new RuntimeException("Simulate a failure"));
     *         else return Either.right(null);
     *     })
     *             .limit(100)
     *             .collect(Either.collector())
     *             .<String>fold(lefts -> lefts.stream().map(Exception::getMessage).collect(Collectors.joining("\n")),
     *                     rights -> "There were " + rights.size() + " right elements."));
     * </code>
     */
    public static <LL, RR> Collector<Either<LL, RR>, EitherCollector<LL, RR>, EitherCollection<LL,RR>> collector() {
        return new Collector<Either<LL, RR>, EitherCollector<LL, RR>, EitherCollection<LL,RR>>() {
            @Override
            public Supplier<EitherCollector<LL, RR>> supplier () {
                return EitherCollector::new;
            }

            @Override
            public BiConsumer<EitherCollector<LL, RR>, Either<LL, RR>> accumulator () {
                return EitherCollector::add;
            }

            @Override
            public BinaryOperator<EitherCollector<LL, RR>> combiner () {
                return EitherCollector::addAll;
            }

            @Override
            public Function<EitherCollector<LL, RR>, EitherCollection<LL, RR>> finisher () {
                return EitherCollector::collect;
            }

            @Override
            public Set<Characteristics> characteristics () {
                return Collections.emptySet();
            }
        };
    }

    /** Used in the Either.collector() as an intermediate result of collecting over a stream of Either objects */
    private static final class EitherCollector<LL, RR> {
        private final Stream.Builder<LL> lefts = Stream.builder();
        private final Stream.Builder<RR> rights = Stream.builder();

        private void add(final Either<LL, RR> anEither) {
            if (anEither.isLeft()) {
                lefts.add(anEither.getLeft());
            } else {
                rights.add(anEither.getRight());
            }
        }

        private EitherCollector<LL, RR> addAll(final EitherCollector<LL, RR> partial) {
            partial.lefts.build().forEach(lefts::add);
            partial.rights.build().forEach(rights::add);
            return this;
        }

        public EitherCollection<LL, RR> collect() {
            return new EitherCollection<>(
                    lefts.build().collect(Collectors.toList()),
                    rights.build().collect(Collectors.toList()));
        }
    }

    /** An implementation of the Either class representing the Left value */
    private static class Left<LL, RR> extends Either<LL, RR> {
        private final LL v;
        private Left(final LL _v) { super(token); v = _v; }

        @Override public <T> Either<T, RR> mapLeft(Function<LL, T> fn) { return Either.left(fn.apply(v)); }

        @Override public <T> Either<LL, T> mapRight(Function<RR, T> fn) { return Either.left(v); }

        @Override public <T> T map(Function<LL, T> fnLeft, Function<RR, T> fnRight) { return fnLeft.apply(v); }

        @Override public Either<LL, RR> mapRightToLeft(Predicate<RR> testRValue, Function<RR, LL> newLValueGenerator) { return this; }

        @Override public boolean isLeft() { return true; }

        @Override public LL getLeft() { return v; }

        @Override public RR getRight() { throw new IllegalStateException("Cannot get a Right value from a Left Either"); }
    }

    /** An implementation of the Either class representing the Right value */
    private static final class Right<LL, RR> extends Either<LL, RR> {
        private final RR v;
        private Right(final RR _v) { super(token); v = _v; }

        @Override public <T> Either<T, RR> mapLeft(Function<LL, T> fn) { return Either.right(v); }

        @Override public <T> Either<LL, T> mapRight(Function<RR, T> fn) { return Either.right(fn.apply(v)); }

        @Override public <T> T map(Function<LL, T> fnLeft, Function<RR, T> fnRight) { return fnRight.apply(v); }

        @Override public Either<LL, RR> mapRightToLeft(Predicate<RR> testRValue, Function<RR, LL> newLValueGenerator) {
            if (testRValue.test(v)) {
                return Either.left(newLValueGenerator.apply(v));
            }
            return this;
        }

        @Override public boolean isLeft() { return false; }

        @Override public LL getLeft() { throw new IllegalStateException("Cannot get a Left value from a Right Either"); }

        @Override public RR getRight() { return v; }
    }

    /** Construct a Left Either from the supplied value */
    public static <LL, RR> Either<LL, RR> left(LL v) {
        return new Left<>(v);
    }

    /** Construct a Right Either from the supplied value */
    public static <LL, RR> Either<LL, RR> right(RR v) {
        return new Right<>(v);
    }

    public static <LL, RR> Either<LL, RR> rightOrElse(final Optional<RR> _right, final Supplier<LL> _leftSupplier) {
        return _right.<Either<LL, RR>>map(Either::right).orElseGet(() -> left(_leftSupplier.get()));
    }

    /**
     * Generate a new Either by applying the mapping function to the left value.  If this is a Right either,
     * only the type signature will change.
     * @param fn the function to apply to the left value if this is a Left either
     * @param <T> the new type of the left value
     * @return a new Either with the Left value mapped by the function or the right value unchanged
     */
    public abstract <T> Either<T, R> mapLeft(Function<L, T> fn);
    /**
     * Generate a new Either by applying the mapping function to the right value.  If this is a Left either,
     * only the type signature will change.
     * @param fn the function to apply to the right value if this is a Right either
     * @param <T> the new type of the right value
     * @return a new Either with the Right value mapped by the function or the left value unchanged
     */
    public abstract <T> Either<L, T> mapRight(Function<R, T> fn);

    /**
     * Convert this Either to an object of type T by applying the appropriate transformation function depending on
     * whether this is a Left or Right Either.
     * @param fnLeft the function used to transform the value of a Left Either
     * @param fnRight the function used to transform the value of a Right Either
     * @param <T> the type that either the Left or Right value should be mapped to
     * @return an object of type T after the appropriate transformation function has been applied
     */
    public abstract <T> T map(Function<L, T> fnLeft, Function<R, T> fnRight);

    /**
     * Apply the test to the right value and return a left Either if the test passes.
     * This mapping function should be used to test if a right value is invalid and convert it to an appropriate error message
     * if so.  If this is already a left either, this is immediately returned.
     * If this is a right either, the value is passed to the testRValue {@link Predicate}.
     * If the test returns false, this right either is returned unmodified.
     * If the test returns true, a new left either will be returned with the value from the supplied newLValueGenerator.
     * @param testRValue a function which tests R values to determine if they should be mapped to L values
     * @param newLValueGenerator a function which generates a new L value from an R value
     * @return this either if this either is a left OR this either if this either is a right and the supplied predicate
     * returns false, otherwise a new left either created by mapping the right value
     */
    public abstract Either<L, R> mapRightToLeft(Predicate<R> testRValue, Function<R, L> newLValueGenerator);

    /** Return true if this is a Left Either and false if it is a Right Either */
    public abstract boolean isLeft();

    /** Get the value out of a Left Either or throw an IllegalStateException if this is a Right Either */
    public abstract L getLeft();
    /** Get the value out of a Right Either or throw an IllegalStateException if this is a Left Either */
    public abstract R getRight();

    private static final class ExternalSubclassesNotAllowed {}
    private static final ExternalSubclassesNotAllowed token = new ExternalSubclassesNotAllowed();
    /** This class cannot be extended beyond the two subclasses defined in this file */
    private Either(final ExternalSubclassesNotAllowed _token) {
        if (_token == null)
            throw new IllegalArgumentException("Only the subclasses defined in the Either class may exist");
    }

    public static void main(final String[] args) {
        final Random rand = new Random();
        final double failPercent = 0;
        final String result = Stream
                .<Either<Exception,Integer>>generate(() -> {
                    if (rand.nextDouble() < failPercent) {
                        return Either.left(new RuntimeException("This is a failure."));
                    } else {
                        return Either.right(rand.nextInt());
                    }
                })
                .limit(100)
                .collect(Either.collector())
                .fold(lefts -> lefts.stream().map(Exception::getMessage).collect(Collectors.joining("\n")),
                        rights -> rights.stream().map(Object::toString).collect(Collectors.joining()));
        System.out.println(result);

        System.out.println(Stream.<Either<Exception, Object>>generate(() -> {
            if (rand.nextBoolean()) return Either.left(new RuntimeException("Simulate a failure"));
            else return Either.right(null);
        })
                .limit(100)
                .collect(Either.collector())
                .<String>fold(lefts -> lefts.stream().map(Exception::getMessage).collect(Collectors.joining("\n")),
                        rights -> "There were " + rights.size() + " right elements."));
    }
}
