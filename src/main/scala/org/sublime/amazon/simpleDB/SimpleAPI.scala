package org.sublime.amazon.simpleDB {
	import scala.xml.PrettyPrinter
	
	class SimpleConnection (id:String, key:String) 
		extends Connection(id, key)
	{			
		class Item(val domain:Domain, val name:String) {
		    
		    override def toString = domain + "." + name
		    
			/**
			 * Read all of the attributes from this item.
			 */
			def attributes = (new GetAttributesRequest(domain.name, name, Set()))
				.response.result.attributes
			
			/**
			 * Read a selection of attributes from this item
			 */
			def attributes (attributes:Set[String]) = 
				(new GetAttributesRequest(domain.name, name, Set())).response.result.attributes
				
			/**
			 * Read a single attribute from this item.
			 */
			def attribute (attributeName:String) =
				(new GetAttributesRequest(domain.name, name, Set(attributeName)))
					.response.result.attributes(attributeName)
			
			def putAttribute (pair :(String, String), replace:Boolean) = {
				(new PutAttributesRequest(domain.name, name, 
						Map(pair._1 -> (pair._2 -> replace))
					)
				).response.metadata				
			}
					
			def putAttribute (name:String, values:Set[String], replace:Boolean) = {
			    (new PutAttributesRequest(domain.name, name, 
			            Map() ++ (values map (value => (name -> (value -> replace))))
			        )
			    ).response.metadata
			}
			
			def update (values:Map[String, (String, Boolean)]) = {
			    (new PutAttributesRequest(domain.name, name, values)).response.metadata
			}
					
			/**
			 * Add a single attribute to this item.
			 */
			def += (pair:(String, String)) = putAttribute(pair, false)
			
			def += (name:String, values:Set[String]) = putAttribute(name, values, false)

			/**
			 * Replace a single attribute in this item.
			 */
			def set (pair:(String,String)) = putAttribute(pair, true)
		
		    def set (name:String, values:Set[String]) = putAttribute(name, values, true)
		
			/** 
			 * Delete all of the attributes in this item.
			 */
			def clear = {
				(new DeleteAttributesRequest(domain.name, name, Map()).response.metadata)
			}
			
			/**
			 * Delete a single attribute value pair in this item.
			 */
			def -= (pair :(String, String)) = {
				(new DeleteAttributesRequest(domain.name, name, Map(pair._1 -> Set(pair._2))))
					.response.metadata
			}		
			
			/**
			 * Delete a single attribute in this item.
			 */
			def -= (name:String) = {
				(new DeleteAttributesRequest(domain.name, name, Map(name -> Set())))
					.response.metadata
			}
		}
		
		class Domain(val name:String) {
			def metadata = (new DomainMetadataRequest(name)).response.result
			def query (expression:String) = 
				(QueryRequest.start(name, Some(expression))).response.result.itemNames map (
					item(_)
				)					
					
			def delete = (new DeleteDomainRequest(name)).response.metadata
			def create = (new CreateDomainRequest(name)).response.metadata
			def item (name:String) = new Item(this, name)
			
			def items :Stream[Item] = {
			    def generate (res:QueryResponse) :Stream[Item] =
			        streamOfObjects(res.result.itemNames.toList, item)
			    def responses (req:QueryRequest, res:QueryResponse) :Stream[QueryResponse] =
			        Stream.cons(res, QueryRequest.next(req, res) match {
			            case None => Stream.empty
			            case Some(request) => responses(request, request.response)
			        })
			    val start = QueryRequest.start(name, None)
			    streamOfStreams(responses(start, start.response), generate)
			}
			
			override def toString = name			
		}
		
		def domain (name:String) = new Domain(name)
					
		// given a stream of generators of generators, which generate type T, and a
		// function to convert a generator into a stream of T, produce a single stream
		// that yields all of the Ts from each generator.
		def streamOfStreams [T, G] (sources:Stream[G], generate:(G => Stream[T])) :Stream[T] =
		{		  		    
		    def next (sources:Stream[G], generated:Stream[T]) :Stream[T] = 
	            if (generated.isEmpty) {
	                if (sources.isEmpty) Stream.empty
	                else next (sources.tail, generate(sources.head))
	            } else Stream.cons(generated.head, next(sources, generated.tail))
		    
		    next(sources, Stream.empty)
		}		
		
		// given a list of type K, and a function to convert from K to T, produce
		// a stream that performs the conversion as each element is accessed
		def streamOfObjects [T, K] (list:List[K], convert: (K => T)) :Stream[T] =
		{
		    def makeStream (remaining:List[K]) :Stream[T] = {
		        remaining match {
	                case List() => Stream.empty
	                case head :: tail => Stream.cons(convert(head), makeStream(tail))
	            }
            }
	        makeStream(list)	        
		}
			
		def domains :Stream[Domain] = {
		    def convert (name:String) = new Domain(name)
		    def generate (res:ListDomainsResponse) :Stream[Domain] = 
		        streamOfObjects(res.result.domainNames.toList, convert)
		    def responses (response:ListDomainsResponse) :Stream[ListDomainsResponse] =
		        Stream.cons(response, ListDomainsRequest.next(response) match {
		            case None => Stream.empty
		            case Some(request) => responses(request.response)
		        })
		    streamOfStreams(responses(ListDomainsRequest.start.response), generate)
		}
						    
		//// Simple test methods.
		
		def listDomains {
			Console.println(ListDomainsRequest.start.response.result)
		}
		
		def createDomain (name:String) {
			Console.println((new CreateDomainRequest(name)).response.metadata)
		}
		
		def deleteDomain (name:String) {
			Console.println((new DeleteDomainRequest(name)).response.metadata)
		}
		
		def domainMetadata (name:String) {
			Console.println((new DomainMetadataRequest(name)).response.result)
		}
		
		def putAttributes (domain:String, item:String, attributes:Map[String ,(String, Boolean)]) {
		    Console.println((new PutAttributesRequest(domain, item, attributes)).response.metadata)
		}
		
		def deleteAttributes (domain:String, item:String, attributes:Map[String, Set[String]]) {
		    Console.println((new DeleteAttributesRequest(domain, item, attributes)).response.metadata)
		}
		
		def getAttributes (domain:String, item:String, attributes:Set[String]) {
		    Console.println((new GetAttributesRequest(domain, item, attributes)).response.result)
		}
		
		def query (domain:String, query:String) {
		    Console.println((QueryRequest.start(domain, Some(query))).response.result)
		}
		
		def queryWithAttributes (domain:String, query:String, attributes:Set[String]) {
		    Console.println((new QueryWithAttributesRequest(domain, query, attributes)).response.result)
		}
	}
}