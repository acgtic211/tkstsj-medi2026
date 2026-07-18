TODO: Fix all queries

Spatial Keyword Query Processing: An Experimental Evaluation
Location- and keyword-based querying of geo-textual data: a survey


# Query Types
3.1.1 Standard queries
In general, a standard spatial keyword query takes as input a spatial parameter (s) and a textual parameter (t), and it returns one or more geo-textual objects, which can be either rank-ordered or not.
Spatial parameter The typical spatial parameter is a point location that models the location of the user who issues the
query or a location of interest. The spatial parameter can also be a set of point locations or a region.
Textual parameter The textual parameter takes two different forms: a Boolean keyword expression or a set of keywords.
A Boolean keyword expression consists of a set of keywords connected by AND or OR operators. It is used for finding
objects whose textual content satisfies the expression. The textual parameter in the form of a set of keywords is adopted
mostly in top-k queries that rank the objects based on a function that quantifies the textual relevance between the query
and the objects.
The standard spatial keyword queries either involve only Boolean predicates on the spatial and textual (and possibly
other) object attributes or return the top-k objects that satisfy optional Boolean predicates according to a ranking function.
Specifically, first, a spatial keyword Boolean predicate takes the form P st (s, t, o), where s and t are spatial and textual
parameters, and o is a geo-textual object. Such a predicate can be expressed as a conjunction of a spatial predicate and
a textual predicate: P s (s, o) and P t (t, o). Range and containment predicates are common spatial and textual predicates,
respectively. The cardinality of the result may vary from 0 to the size of the set of objects. Second, for a ranking query, the
ranking function can be any function that takes an object as an argument and assigns a score to it.
Let D be a set of geo-textual objects. Each object o ∈ D is defined as a pair (o.ρ, o.ψ), where o.ρ is a two-dimensional
Euclidean point location and o.ψ is its text content. We consider different types of queries based on the ways they use
spatial and textual predicates.



## Boolean range SK query (BRSK)
Boolean range spatial keyword (BRSK) query [35,39,43,65, 80,100,134,162,176] A BRSK query q = (B E, ρ, r ) takes
three parameters: B E is a Boolean keyword expression that is composed of a set of keywords connected by AND or OR
operators, ρ is a query location, and r is a query region radius.
A BRSK query can be considered as the combination of a Boolean query from information retrieval and a range query
from spatial databases. Formally, the result q(D) of q is the subset of D satisfying ∀o ∈ q(D)(dist(o, q) ≤ q.r ∧ q.B E(o.ψ)).
Here, q.B E can be represented by q.ψ1 ∨q.ψ2 ∨· · ·∨q.ψm , where q.ψi (1 ≤ i ≤ m) is a set of query keywords, and
q.B E(o.ψ) returns true if ∃1 ≤ i ≤ m(q.ψ i ⊆ o.ψ).
Most existing studies (e.g., [35,39,43,65,100,134,176]) consider a simplified BRSK query that considers a Boolean
keyword expression composed of AND operators only. This query is given by q  = (ψ, ρ, r ), and the result q (D) of q 
is the subset of D satisfying ∀o ∈ q  (D)(dist(o, q) ≤ q.r ∧ q.ψ ⊆ o.ψ).
One study considers an approximate keyword range query [10], which has an approximate keyword constraint (e.g.,
based on edit distance or Jaccard similarity). This query finds objects that are textually more similar to the query keywords
than a threshold and belong to a given spatial query range.


## Boolean kNN SK query (BkSK)
Boolean kNN spatial keyword (BkSK) query [19,52,95,132,145,157,162] A BkSK query q = (B E, ρ, k) takes three
parameters, where B E is a Boolean keyword expression as stated in the BRSK query, ρ is a spatial point, and k is the
number of objects to retrieve. The query combines a Boolean keyword query from information retrieval and a kNN query
from spatial databases. The result q(D) of a BkSK query is a set of (at most) k objects, each of which satisfies the Boolean
keyword expression q.B E. The objects are ranked according to their distances to ρ. Formally, ∀o ∈ q(D)((o ∈ D \
q(D))(dist(o , q) < dist(o, q)) ∧ q.B E(o.ψ)).
Example Retrieve the k objects nearest to the Hyatt Regency Hotel, San Francisco, USA, such that each object’s
text contains the keywords sushi and ramen.


## Top-k kNN SK query (TkSK)
IR-Tree: An Efficient Index for Geographic Document Search

Top-k kNN spatial keyword (T kSK) query [42,55,88,120,151,152,157,162] A TkSK query q = (ψ, ρ, k) takes three
parameters: ψ is a set of keywords, ρ is a spatial point, and k is the number of objects to retrieve. The query
result q(D) is a set of (at most) k objects. The objects are ranked according to a score that takes into considera-
tion spatial proximity and textual relevance. Formally, ∀o ∈ q(D)((o ∈ D \ q(D))(ST (o  , q) < ST (o, q))), where
the ranking score ST (o, q) can be defined by ST a (o, q) [42,55,88,151,157,162] or ST b (o, q) [120] that are defined
as follows:
ST a (o, q) = α · ss(o, q) + (1 − α) · (1 − st(o, q))
ss(o, q)
STb (o, q) =
st(o, q)

Here, ss(o, q) is the spatial proximity of o to q, st(o, q) is the textual relevance of o to q, and α ∈ [0, 1) in Eq. 1
is a preference parameter that makes it possible to balance spatial proximity and textual relevance. In Eq. 2, the com-
bination between spatial proximity and textual relevance is represented as a fraction, thus eliminating the query preference parameter.
In these definitions, the spatial proximity is defined as the normalized Euclidean distance: ss(o, q) = dist(o,q)
dist max , where
dist(·, ·) denotes Euclidean distance, and distmax is the maximum distance between any two objects in D. Further, the
textual relevance st(·, ·) can be computed using an information retrieval model, such as the language model (e.g., [42]),
cosine similarity (e.g., [120]), or BM25 (e.g., [39]) that is normalized to a scale similar to the spatial proximity.
One study [78] considers a different TkSK query where the spatial part of an object is a region (i.e., a rectangle or
another shape) and where the spatial part of the query is a set of rectangles. The ranking score is computed using Eq. 1.
Example Retrieve the k objects with the highest ranking scores with respect to the location of the Hyatt Regency
Hotel, San Francisco, USA, and the keywords quiet, pizza, and cappuccino.

