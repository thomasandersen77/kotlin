// !DIAGNOSTICS: -UNUSED_PARAMETER

interface Iface<M, T>

interface IfaceProvider<M : Iface<M, *>>

class C<T> : Iface<C<*>, T>
class D<T> : Iface<D<*>, T>

class CProvider : IfaceProvider<C<*>>

fun <M : Iface<M, *>, T> withProvider(provider: IfaceProvider<M>, block: () -> Iface<M, T>) {}

fun test() {
    <!TYPE_INFERENCE_CONFLICTING_SUBSTITUTIONS!>withProvider<!>(CProvider()) { D<Int>() }
}