import io, urllib.request
from PIL import Image, ImageDraw, ImageFont
F='/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
URL='https://srdlogistics.com/wp-content/uploads/2019/02/cropped-logo1-4-1.png'
data=urllib.request.urlopen(urllib.request.Request(URL,headers={'User-Agent':'Mozilla/5.0'})).read()
L=Image.open(io.BytesIO(data)).convert('RGBA')
em=L.crop((10,0,150,170)); em=em.crop(em.split()[3].getbbox())
wm=L.crop((175,0,580,112)); wm=wm.crop(wm.split()[3].getbbox())
S=512
def put(cv,img,cx,cy,h=None,w=None):
    if h: img=img.resize((int(img.width*h/img.height),h),Image.LANCZOS)
    if w: img=img.resize((w,int(img.height*w/img.width)),Image.LANCZOS)
    cv.alpha_composite(img,(int(cx-img.width/2),int(cy-img.height/2)))
def art():
    cv=Image.new('RGBA',(S,S),(0,0,0,0))
    put(cv,em,256,150,h=190)
    put(cv,wm,256,320,w=340)
    d=ImageDraw.Draw(cv); f=ImageFont.truetype(F,50); t='MARKETING'
    b=d.textbbox((0,0),t,font=f)
    d.text((S/2-(b[2]-b[0])/2-b[0],420-(b[3]-b[1])/2-b[1]),t,font=f,fill=(255,255,255,255))
    return cv
a=art()
card=Image.new('RGBA',(S,S))
px=card.load()
for y in range(S):
    for x in range(S):
        t=y/S
        px[x,y]=(int(28-16*t),int(28-16*t),int(32-18*t),255)
m=Image.new('L',(S,S),0); ImageDraw.Draw(m).rounded_rectangle([0,0,S-1,S-1],radius=112,fill=255)
leg=Image.new('RGBA',(S,S),(0,0,0,0)); leg.paste(card,(0,0),m); leg.alpha_composite(a)
leg.resize((192,192),Image.LANCZOS).save('ic_srd.png',optimize=True)
box=a.crop(a.split()[3].getbbox())
k=200/max(box.size)
box=box.resize((int(box.width*k),int(box.height*k)),Image.LANCZOS)
fg=Image.new('RGBA',(324,324),(0,0,0,0))
fg.alpha_composite(box,(162-box.width//2,162-box.height//2))
fg.save('ic_srd_fg.png',optimize=True)
