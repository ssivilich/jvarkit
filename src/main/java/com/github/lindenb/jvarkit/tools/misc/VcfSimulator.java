/*
The MIT License (MIT)

Copyright (c) 2014 Pierre Lindenbaum

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.


History:
* 2014 creation

*/
package com.github.lindenb.jvarkit.tools.misc;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import htsjdk.samtools.reference.IndexedFastaSequenceFile;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.CloserUtil;

import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.vcf.VCFFormatHeaderLine;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLine;
import htsjdk.variant.vcf.VCFHeaderLineType;

import com.github.lindenb.jvarkit.util.AbstractCommandLineProgram;
import com.github.lindenb.jvarkit.util.picard.GenomicSequence;
import com.github.lindenb.jvarkit.util.vcf.VCFUtils;

public class VcfSimulator extends AbstractCommandLineProgram
	{
	private Random random;
	private IndexedFastaSequenceFile indexedFastaSequenceFile;
	private Set<String> samples=new HashSet<String>();
	private Integer numSamples=null;
	
	private VcfSimulator()
		{
		}
	

	@Override
	public String getProgramDescription() {
		return "Generate a VCF";
		}
	@Override
	protected String getOnlineDocUrl() {
		return DEFAULT_WIKI_PREFIX+"VcfSimulator";
		}
	
	

	@Override
	public void printOptions(PrintStream out)
		{
		out.println(" -R (file) Fasta Reference (REQUIRED) .");
		out.println(" -S (long) seed .");
		super.printOptions(out);
		}
	@Override
	public int doWork(String[] args)
		{
		long seed=System.currentTimeMillis();

		File reference=null;
		com.github.lindenb.jvarkit.util.cli.GetOpt opt=new com.github.lindenb.jvarkit.util.cli.GetOpt();
		int c;
		while((c=opt.getopt(args,getGetOptDefault()+ "S:R:"))!=-1)
			{
			switch(c)
				{
				case 'S': seed=Long.parseLong(opt.getOptArg());break;
				case 'R': reference=new File(opt.getOptArg());break;
				default: 
					{
					switch(handleOtherOptions(c, opt, null))
						{
						case EXIT_FAILURE:return -1;
						case EXIT_SUCCESS: return 0;
						default:break;
						}
					}
				}
			}
		if(reference==null)
			{
			error("Reference is undefined");
			return -1;
			}
		this.random=new Random(seed);
		if(numSamples==null) numSamples=1+this.random.nextInt(10);
		while(this.samples.size()<numSamples) this.samples.add("SAMPLE"+(1+this.samples.size()));
		VariantContextWriter writer=null;
		try
			{
			this.indexedFastaSequenceFile=new IndexedFastaSequenceFile(reference);
			Set<VCFHeaderLine> metaData=new HashSet<VCFHeaderLine>();
			metaData.add(new VCFFormatHeaderLine(
					"GT",
					1,
					VCFHeaderLineType.String,
					"Genotype"));
			metaData.add(new VCFFormatHeaderLine(
					"DP",
					1,
					VCFHeaderLineType.Integer,
					"Depth"));
			
			VCFHeader header=new VCFHeader(
					metaData,
					this.samples);
			header.setSequenceDictionary(this.indexedFastaSequenceFile.getSequenceDictionary());
			
			writer=VCFUtils.createVariantContextWriterToStdout();
			
			
			writer.writeHeader(header);
			for(;;)
				{
				if(System.out.checkError()) break;
				for(SAMSequenceRecord ssr: this.indexedFastaSequenceFile.getSequenceDictionary().getSequences())
					{
					if(System.out.checkError()) break;
					GenomicSequence genomic=new GenomicSequence(this.indexedFastaSequenceFile, ssr.getSequenceName());
					for(int pos=1;pos<=ssr.getSequenceLength();++pos)
						{
						if(System.out.checkError()) break;
						char REF=Character.toUpperCase(genomic.charAt(pos-1));
						if(REF=='N') continue;
						char ALT='N';
						switch(REF)
							{
							case 'A': ALT="TGC".charAt(random.nextInt(3));break;
							case 'T': ALT="AGC".charAt(random.nextInt(3));break;
							case 'G': ALT="TAC".charAt(random.nextInt(3));break;
							case 'C': ALT="TGA".charAt(random.nextInt(3));break;
							default: ALT='N';
							}
						if(ALT=='N') continue;
						Allele refAllele=Allele.create((byte)REF,true);
						Allele altAllele=Allele.create((byte)ALT,false);
						VariantContextBuilder cb=new VariantContextBuilder();
						cb.chr(genomic.getChrom());
						cb.start(pos);
						cb.stop(pos);
						List<Genotype> genotypes=new ArrayList<Genotype>(samples.size());
						for(String sample: samples)
							{
							Allele a1=(random.nextBoolean()?refAllele:altAllele);
							Allele a2=(random.nextBoolean()?refAllele:altAllele);
							GenotypeBuilder gb=new GenotypeBuilder(
									sample,
									Arrays.asList(a1,a2)
									);
							if(random.nextBoolean())
								{
								gb=new GenotypeBuilder(
										sample,
										Arrays.asList(a1,a2)
										);
								gb.DP(1+random.nextInt(50));
								
								}
							genotypes.add(gb.make());
							}
						cb.genotypes(genotypes);
						cb.alleles(Arrays.asList(refAllele,altAllele));
						writer.add(cb.make());
						}
					
					}
				}

			return 0;
			}
		catch(Exception err)
			{
			error(err);
			return -1;
			}
		finally
			{
			CloserUtil.close(this.indexedFastaSequenceFile);
			CloserUtil.close(writer);
			}
		}

	public static void main(String[] args)
		{
		new VcfSimulator().instanceMainWithExit(args);
		}
	}
