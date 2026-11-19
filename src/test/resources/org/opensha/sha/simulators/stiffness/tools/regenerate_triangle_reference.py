"""Regenerate the frozen Java test oracle with cutde==26.3.6 (CPU, float64).

Requires NumPy and cutde only when regenerating; normal Java tests read the CSV.
Run this script from any directory. No OpenSHA dependency is added.
"""
import os, csv
from importlib.metadata import version
os.environ['CUTDE_USE_BACKEND']='cpp'; os.environ['OMP_NUM_THREADS']='1'
assert version('cutde') == '26.3.6', 'Use the pinned oracle version cutde==26.3.6'
import numpy as np
from cutde.halfspace import strain
from pathlib import Path
rng=np.random.default_rng(871335)
rows=[]
for i in range(240):
    tri=rng.normal(size=(3,3))*2000
    tri[:,2]=-np.abs(tri[:,2])-100
    obs=rng.normal(size=3)*10000; obs[2]=-abs(obs[2])
    slip=rng.normal(size=3)
    if i%4==0: obs=tri.mean(axis=0)+np.array([0.0123,0.0345,-0.0567])
    if i%6==0:tri[:,2]=-2000
    if i%7==0:tri[:,1]=0
    if i%8==0:tri[0,2]=0
    if i%9==0:obs[2]=0
    if i%10==0:tri[2]=tri[0]+.9*(tri[1]-tri[0])+np.array([.1,.2,.3])
    if i%11==0:slip=np.array([1.,0.,0.])
    if i%13==0:slip=np.array([0.,1.,0.])
    nu=rng.uniform(.1,.4)
    if np.linalg.norm(np.cross(tri[1]-tri[0],tri[2]-tri[0])) == 0: continue
    expected=strain(obs[None,:],tri[None,:,:],slip[None,:],nu)[0]
    if not np.all(np.isfinite(expected)): raise RuntimeError(i)
    rows.append([i,*tri.ravel(),*obs,*slip,nu,*expected])
p=Path(__file__).resolve().parent.parent/'triangle_cutde_reference.csv'
with p.open('w') as f:
    f.write('# cutde 26.3.6 CPU float64, seed 871335; id,vertices(9),observation(3),ss,ds,opening,nu,exx,eyy,ezz,exy,exz,eyz\n')
    csv.writer(f,lineterminator='\n').writerows(rows)
print('Wrote',len(rows),'cutde reference cases')
