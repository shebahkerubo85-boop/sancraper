package ani.sanin.universal.discovery

object TitleMatcher {
 fun score(a:String,b:String):Double { val x=norm(a); val y=norm(b); if(x.isBlank()||y.isBlank())return 0.0; if(x==y)return 1.0; val ax=x.split(' ').toSet(); val by=y.split(' ').toSet(); val overlap=ax.intersect(by).size.toDouble()/maxOf(1,ax.union(by).size); val contains=if(x.contains(y)||y.contains(x)) .25 else 0.0; return minOf(1.0,overlap+.25*contains) }
 private fun norm(s:String)=s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+")," ").trim().replace(Regex("\\s+")," ")
}
