/*
 * Java Genetic Algorithm Library (@__identifier__@).
 * Copyright (c) @__year__@ Franz Wilhelmstötter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Author:
 *    Franz Wilhelmstötter (franz.wilhelmstoetter@gmail.com)
 */
package io.jenetics.ext.moea;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static io.jenetics.ext.moea.Pareto.front;

import java.util.Comparator;
import java.util.function.ToIntFunction;
import java.util.stream.Collector;

import io.jenetics.Gene;
import io.jenetics.Optimize;
import io.jenetics.Phenotype;
import io.jenetics.engine.EvolutionResult;
import io.jenetics.util.ISeq;
import io.jenetics.util.IntRange;

/**
 * Collectors for collecting final <em>pareto-set</em> for multi-objective
 * optimization.
 *
 * <pre>{@code
 *  final Problem<double[], DoubleGene, Vec<double[]>> problem = Problem.of(
 *      v -> Vec.of(v[0]*cos(v[1]), v[0]*sin(v[1])),
 *      Codecs.ofVector(
 *          DoubleRange.of(0, 1),
 *          DoubleRange.of(0, 2*PI)
 *      )
 *  );
 *
 *  final Engine<DoubleGene, Vec<double[]>> engine = Engine.builder(problem)
 *      .alterers(
 *          new Mutator<>(0.1),
 *          new MeanAlterer<>())
 *      .offspringSelector(new TournamentSelector<>(2))
 *      .survivorsSelector(UFTournamentSelector.vec())
 *      .build();
 *
 *  final ISeq<Phenotype<DoubleGene, Vec<double[]>>> result = engine.stream()
 *      .limit(Limits.byFixedGeneration(50))
 *      .collect(MOEA.toParetoSet());
 * }</pre>
 *
 *
 * @author <a href="mailto:franz.wilhelmstoetter@gmail.com">Franz Wilhelmstötter</a>
 * @version 4.1
 * @since 4.1
 */
public final class MOEA {

	private MOEA() {
	}

	/**
	 * Collector of {@link Phenotype} objects, who's (multi-objective) fitness
	 * value is part of the <a href="https://en.wikipedia.org/wiki/Pareto_efficiency">
	 *     pareto front</a>.
	 *
	 * @param <G> the gene type
	 * @param <T> the array type, e.g. {@code double[]}
	 * @param <V> the multi object result type vector
	 * @return the pareto set collector
	 * @throws IllegalArgumentException if the minimal pareto set {@code size}
	 *         is smaller than one
	 */
	public static <G extends Gene<?, G>, T, V extends Vec<T>>
	Collector<EvolutionResult<G, V>, ?, ISeq<Phenotype<G, V>>>
	toParetoSet() {
		return toParetoSet(IntRange.of(75, 100));
	}

	/**
	 * Collector of {@link Phenotype} objects, who's (multi-objective) fitness
	 * value is part of the <a href="https://en.wikipedia.org/wiki/Pareto_efficiency">
	 *     pareto front</a>.
	 *
	 * @param size the allowed size range of the returned pareto set. If the
	 *        size of the pareto set is bigger than {@code size.getMax()},
	 *        during the collection, it is reduced to {@code size.getMin()}.
	 *        Pareto set elements which are close to each other are removed firsts.
	 * @param <G> the gene type
	 * @param <T> the array type, e.g. {@code double[]}
	 * @param <V> the multi object result type vector
	 * @return the pareto set collector
	 * @throws NullPointerException if one the {@code size} is {@code null}
	 * @throws IllegalArgumentException if the minimal pareto set {@code size}
	 *         is smaller than one
	 */
	public static <G extends Gene<?, G>, T, V extends Vec<T>>
	Collector<EvolutionResult<G, V>, ?, ISeq<Phenotype<G, V>>>
	toParetoSet(final IntRange size) {
		return toParetoSet(
			size,
			Vec<T>::dominance,
			Vec<T>::compare,
			Vec<T>::distance,
			Vec<T>::length
		);
	}

	/**
	 * Collector of {@link Phenotype} objects, who's (multi-objective) fitness
	 * value is part of the <a href="https://en.wikipedia.org/wiki/Pareto_efficiency">
	 *     pareto front</a>.
	 *
	 * @see #toParetoSet(IntRange)
	 *
	 * @param size the allowed size range of the returned pareto set. If the
	 *        size of the pareto set is bigger than {@code size.getMax()},
	 *        during the collection, it is reduced to {@code size.getMin()}.
	 *        Pareto set elements which are close to each other are removed firsts.
	 * @param dominance the pareto dominance measure of the fitness result type
	 *        {@code C}
	 * @param comparator the comparator of the elements of the vector type
	 *        {@code C}
	 * @param distance the distance function of two elements of the vector
	 *        type {@code C}
	 * @param dimension the dimensionality of the result vector {@code C}.
	 *        Usually {@code Vec::length}.
	 * @param <G> the gene type
	 * @param <C> the multi object result vector. E.g. {@code Vec<double[]>}
	 * @return the pareto set collector
	 * @throws NullPointerException if one the arguments is {@code null}
	 * @throws IllegalArgumentException if the minimal pareto set {@code size}
	 *         is smaller than one
	 */
	public static <G extends Gene<?, G>, C extends Comparable<? super C>>
	Collector<EvolutionResult<G, C>, ?, ISeq<Phenotype<G, C>>>
	toParetoSet(
		final IntRange size,
		final Comparator<? super C> dominance,
		final ElementComparator<? super C> comparator,
		final ElementDistance<? super C> distance,
		final ToIntFunction<? super C> dimension
	) {
		requireNonNull(size);
		requireNonNull(dominance);
		requireNonNull(distance);
		if (size.getMin() < 1) {
			throw new IllegalArgumentException(format(
				"Minimal pareto set size must be greater than zero: %d",
				size.getMin()
			));
		}

		return Collector.of(
			() -> new Front<G, C>(
				size, dominance, comparator, distance, dimension),
			Front::add,
			Front::merge,
			Front::toISeq
		);
	}

	private static final class Front<
		G extends Gene<?, G>,
		C extends Comparable<? super C>
	> {

		final IntRange _size;
		final Comparator<? super C> _dominance;
		final ElementComparator<? super C> _comparator;
		final ElementDistance<? super C> _distance;
		final ToIntFunction<? super C> _dimension;

		private Optimize _optimize;
		private ParetoFront<Phenotype<G, C>> _front;

		Front(
			final IntRange size,
			final Comparator<? super C> dominance,
			final ElementComparator<? super C> comparator,
			final ElementDistance<? super C> distance,
			final ToIntFunction<? super C> dimension
		) {
			_size = size;
			_dominance = dominance;
			_comparator = comparator;
			_distance = distance;
			_dimension = dimension;
		}

		void add(final EvolutionResult<G, C> result) {
			if (_front == null) {
				_optimize = result.getOptimize();
				_front = new ParetoFront<>((a, b) ->
					_optimize == Optimize.MAXIMUM
						? _dominance.compare(a.getFitness(), b.getFitness())
						: _dominance.compare(b.getFitness(), a.getFitness())
				);
			}

			final ISeq<Phenotype<G, C>> front = front(
				result.getPopulation(),
				(a, b) -> _optimize == Optimize.MAXIMUM
					? _dominance.compare(a.getFitness(), b.getFitness())
					: _dominance.compare(b.getFitness(), a.getFitness())
			);
			_front.addAll(front.asList());
			trim();
		}

		private void trim() {
			assert _front != null;
			assert _optimize != null;

			if (_front.size() >= _size.getMax() - 1) {
				_front.trim(
					_size.getMin(),
					(a, b, i) -> _optimize == Optimize.MAXIMUM
						? _comparator.compare(a.getFitness(), b.getFitness(), i)
						: _comparator.compare(b.getFitness(), a.getFitness(), i),
					_distance.map(Phenotype::getFitness),
					v -> _dimension.applyAsInt(v.getFitness())
				);
			}
		}

		Front<G, C> merge(final Front<G, C> front) {
			_front.merge(front._front);
			trim();
			return this;
		}

		ISeq<Phenotype<G, C>> toISeq() {
			return _front != null ? _front.toISeq() : ISeq.empty();
		}

	}

}
