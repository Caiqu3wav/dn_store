import Header from "../components/Header";
import Hero from "../components/Hero";
import Products from "../components/Products";
import Categories from "../components/Categories";
import AboutSection from "../components/AboutSection";
import Events from "../components/Events";
import Footer from "../components/Footer";
function Home() {
  return (
    <>
    <Hero />
    <Products/>
    <AboutSection/>
    <Events/>
    <Footer/>
   </>
  );
}

export default Home;